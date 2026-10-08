package dev.meghsohor.meghtv.data.remote

import android.util.JsonReader
import android.util.JsonToken
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

// Published from the repo's channels branch to GitHub Pages.
private const val ListBaseUrl = "https://meghsohor.github.io/meghtv"
private const val SupportedSchemaVersion = 1

private const val UserAgent = "MeghTV (Android; https://github.com/meghsohor/meghtv)"
private val RetryableCodes = setOf(429, 500, 502, 503, 504)

data class ListManifest(val updatedAt: String, val sha256: String, val size: Long, val channels: Int)

class ListCategory(val id: String, val name: String)

class ListCountry(val code: String, val name: String, val flag: String)

/** [country] is empty for a channel shown only in its exclusive categories. */
class ListChannel(val id: String, val name: String, val country: String, val categories: List<String>, val urls: List<String>)

class ChannelList(val categories: List<ListCategory>, val countries: List<ListCountry>, val channels: List<ListChannel>)

class ChannelListClient(private val http: OkHttpClient = OkHttpClient()) {

  suspend fun fetchManifest(): ListManifest =
    withContext(Dispatchers.IO) {
      val json = withRetries { get("$ListBaseUrl/manifest.json").use { it.body?.string() ?: throw IOException("empty manifest") } }
      val root = JSONObject(json)
      checkSchema(root.getInt("schemaVersion"))
      ListManifest(root.getString("updatedAt"), root.getString("sha256"), root.getLong("size"), root.getInt("channels"))
    }

  // Streamed: the list is a few MB, too big to hold as text and a JSON tree at once on a low-end TV.
  // [onBytes] reports the uncompressed bytes read, against the manifest's size.
  suspend fun fetchList(manifest: ListManifest, onBytes: (Long) -> Unit = {}): ChannelList =
    withContext(Dispatchers.IO) {
      withRetries {
        get("$ListBaseUrl/channels.json").use { response ->
          val body = response.body ?: throw IOException("empty channel list")
          val digest = MessageDigest.getInstance("SHA-256")
          val input = DigestInputStream(CountingStream(body.byteStream(), onBytes), digest)
          val list = JsonReader(input.reader()).readList()
          input.copyTo(OutputSink) // the digest needs every byte
          // Pages caches each file on its own for a few minutes, so a new manifest can arrive with the old list.
          if (digest.digest().toHex() != manifest.sha256) throw ListMismatchException()
          check(list.channels.size == manifest.channels) { "the channel list doesn't match its manifest" }
          list
        }
      }
    }

  private fun get(url: String): Response {
    val response = http.newCall(Request.Builder().url(url).header("User-Agent", UserAgent).build()).execute()
    if (response.isSuccessful) return response
    response.close()
    if (response.code in RetryableCodes) throw IOException("GET $url failed: HTTP ${response.code}")
    error("GET $url failed: HTTP ${response.code}")
  }

  // Retries network errors and 429/5xx with backoff; anything else throws at once.
  private suspend fun <T> withRetries(attempts: Int = 3, block: () -> T): T {
    var lastError: IOException? = null
    repeat(attempts) { attempt ->
      try {
        return block()
      } catch (e: IOException) {
        lastError = e
      }
      if (attempt < attempts - 1) delay(400L * (attempt + 1))
    }
    throw lastError ?: IOException("download failed")
  }
}

class ListMismatchException : IllegalStateException("The channel list is being updated. Try again in a few minutes.")

private fun checkSchema(version: Int) = check(version == SupportedSchemaVersion) { "This channel list needs a newer version of MeghTV." }

private fun JsonReader.readList(): ChannelList {
  var categories = emptyList<ListCategory>()
  var countries = emptyList<ListCountry>()
  var channels = emptyList<ListChannel>()
  beginObject()
  while (hasNext()) {
    when (nextName()) {
      "schemaVersion" -> checkSchema(nextInt())
      "categories" -> categories = readArray { readFields { ListCategory(string("id"), string("name")) } }
      "countries" -> countries = readArray { readFields { ListCountry(string("code"), string("name"), string("flag")) } }
      "channels" ->
        channels = readArray { readFields { ListChannel(string("id"), string("name"), string("country"), strings("categories"), strings("urls")) } }
      else -> skipValue()
    }
  }
  endObject()
  return ChannelList(categories, countries, channels)
}

private fun <T> JsonReader.readArray(readItem: JsonReader.() -> T): List<T> {
  val items = ArrayList<T>()
  beginArray()
  while (hasNext()) items += readItem()
  endArray()
  return items
}

/** An object's string and string-list fields, by name; other values are skipped. */
private class Fields(private val values: Map<String, Any>) {
  // Not an IOException: a broken list stays broken, so retrying the download wouldn't help.
  fun string(name: String) = values[name] as? String ?: error("channel list: missing $name")

  @Suppress("UNCHECKED_CAST")
  fun strings(name: String) = values[name] as? List<String> ?: error("channel list: missing $name")
}

private fun <T> JsonReader.readFields(build: Fields.() -> T): T {
  val values = HashMap<String, Any>()
  beginObject()
  while (hasNext()) {
    val name = nextName()
    when (peek()) {
      JsonToken.STRING -> values[name] = nextString()
      JsonToken.BEGIN_ARRAY -> values[name] = readArray { nextString() }
      else -> skipValue()
    }
  }
  endObject()
  return Fields(values).build()
}

private class CountingStream(input: InputStream, private val onBytes: (Long) -> Unit) : FilterInputStream(input) {
  private var count = 0L

  override fun read(): Int = super.read().also { if (it >= 0) onBytes(++count) }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val n = super.read(b, off, len)
    if (n > 0) {
      count += n
      onBytes(count)
    }
    return n
  }
}

private object OutputSink : java.io.OutputStream() {
  override fun write(b: Int) = Unit

  override fun write(b: ByteArray, off: Int, len: Int) = Unit
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

package dev.meghsohor.meghtv.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val DATABASE_RAW = "https://raw.githubusercontent.com/iptv-org/database/master/data"
private const val IPTV_RAW = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams"
private const val IPTV_STREAMS_LISTING = "https://api.github.com/repos/iptv-org/iptv/contents/streams"
// Compiled master playlist on GitHub Pages: every stream in one file, no API rate limit. The refresh fallback.
private const val IPTV_COMBINED = "https://iptv-org.github.io/iptv/index.m3u"

private const val USER_AGENT = "MeghTV (Android; https://github.com/meghsohor/meghtv)"
private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)

class IptvOrgClient(private val http: OkHttpClient = OkHttpClient()) {

  // Retries transient failures (429/5xx/network) with backoff; a 403/404 throws at once so callers can fall back.
  private suspend fun getText(url: String, attempts: Int = 3): String =
    withContext(Dispatchers.IO) {
      var lastError: Exception? = null
      repeat(attempts) { attempt ->
        try {
          http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (response.isSuccessful) return@withContext response.body?.string() ?: error("GET $url returned an empty body")
            if (response.code !in RETRYABLE_CODES) error("GET $url failed: HTTP ${response.code}")
            lastError = IOException("GET $url failed: HTTP ${response.code}")
          }
        } catch (e: IOException) {
          lastError = e
        }
        if (attempt < attempts - 1) delay(400L * (attempt + 1))
      }
      throw lastError ?: IOException("GET $url failed")
    }

  suspend fun fetchChannelsCsv(): String = getText("$DATABASE_RAW/channels.csv")

  suspend fun fetchCategoriesCsv(): String = getText("$DATABASE_RAW/categories.csv")

  suspend fun fetchCountriesCsv(): String = getText("$DATABASE_RAW/countries.csv")

  suspend fun fetchPlaylistCountryCodes(): List<String> {
    val json = getText(IPTV_STREAMS_LISTING)
    // No JSON dependency for one field: the "name": "xx.m3u" values, in order.
    return Regex(""""name"\s*:\s*"([a-z0-9_]+)\.m3u"""").findAll(json).map { it.groupValues[1] }.toList()
  }

  // A few at a time, not 300+ requests at GitHub's CDN at once.
  // [onFetched] runs once per playlist, failed ones included (as ""), concurrently on the caller's dispatcher.
  suspend fun fetchAllPlaylists(countryCodes: List<String>, maxConcurrent: Int = 8, onFetched: (String) -> Unit = {}): List<Pair<String, String>> =
    coroutineScope {
      val semaphore = Semaphore(maxConcurrent)
      countryCodes
        .map { cc ->
          async {
            semaphore.withPermit {
              val text = runCatching { getText("$IPTV_RAW/$cc.m3u") }.getOrDefault("")
              onFetched(text)
              cc to text
            }
          }
        }
        .awaitAll()
        .filter { it.second.isNotBlank() }
    }

  // Fallback when the GitHub API listing is rate-limited (403): one deduplicated playlist, no per-channel mirrors.
  suspend fun fetchCombinedPlaylist(): String = getText(IPTV_COMBINED)
}

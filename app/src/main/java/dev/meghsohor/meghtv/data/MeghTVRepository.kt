package dev.meghsohor.meghtv.data

import androidx.room.withTransaction
import dev.meghsohor.meghtv.data.db.BookmarkEntity
import dev.meghsohor.meghtv.data.db.CategoryEntity
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.data.db.CountryEntity
import dev.meghsohor.meghtv.data.db.DeletedChannelEntity
import dev.meghsohor.meghtv.data.db.FailedChannelEntity
import dev.meghsohor.meghtv.data.db.MeghTVDatabase
import dev.meghsohor.meghtv.data.db.StreamUrlEntity
import dev.meghsohor.meghtv.data.remote.IptvOrgClient
import dev.meghsohor.meghtv.data.remote.M3uEntry
import dev.meghsohor.meghtv.data.remote.parseCsv
import dev.meghsohor.meghtv.data.remote.parseM3u
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class RefreshResult(val total: Int, val added: Int, val removed: Int, val bookmarksRemoved: Int)

sealed interface RefreshProgress {
  data object ChannelInfo : RefreshProgress

  data class Playlists(val done: Int, val total: Int, val channelsFound: Int) : RefreshProgress

  /** The single-file fallback: no per-playlist steps to count. */
  data object CombinedPlaylist : RefreshProgress

  data class Saving(val channels: Int) : RefreshProgress
}

private val TvgIdAttribute = Regex("""tvg-id="([^"]+)"""")

// Under SQLite's default limit of 999 bound parameters per statement.
private const val SqliteMaxBindVariables = 900

class MeghTVRepository(private val db: MeghTVDatabase, private val client: IptvOrgClient = IptvOrgClient()) {

  val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
  val countries: Flow<List<CountryEntity>> = db.countryDao().observeAll()
  val allChannels: Flow<List<ChannelEntity>> = db.channelDao().observeAll()
  val bookmarkedChannels: Flow<List<ChannelEntity>> = db.channelDao().observeBookmarked()

  suspend fun allChannelIds(): List<String> = db.channelDao().allIds()

  suspend fun hasChannels(): Boolean = db.channelDao().hasAny()

  // What refresh does, for data stored before it did.
  suspend fun pruneEmptyMenus() =
    db.withTransaction {
      db.categoryDao().deleteUnused()
      db.countryDao().deleteUnused()
    }

  fun channelsByCategory(categoryId: String): Flow<List<ChannelEntity>> = db.channelDao().observeByCategory(categoryId)

  fun channelsByCountry(countryCode: String): Flow<List<ChannelEntity>> = db.channelDao().observeByCountry(countryCode)

  fun search(query: String): Flow<List<ChannelEntity>> = db.channelDao().observeSearch(query)

  fun channelById(channelId: String): Flow<ChannelEntity?> = db.channelDao().observeById(channelId)

  fun streamUrls(channelId: String): Flow<List<StreamUrlEntity>> = db.streamUrlDao().observeForChannel(channelId)

  fun isBookmarked(channelId: String): Flow<Boolean> = db.bookmarkDao().observeIsBookmarked(channelId)

  suspend fun addBookmark(channelId: String) = db.bookmarkDao().add(BookmarkEntity(channelId, System.currentTimeMillis()))

  suspend fun removeBookmark(channelId: String) = db.bookmarkDao().remove(channelId)

  val failedChannelIds: Flow<List<String>> = db.failedChannelDao().observeIds()

  suspend fun markFailed(channelId: String) = db.failedChannelDao().add(FailedChannelEntity(channelId, System.currentTimeMillis()))

  suspend fun clearFailed(channelId: String) = db.failedChannelDao().remove(channelId)

  suspend fun deleteChannel(channelId: String) = db.deletedChannelDao().add(DeletedChannelEntity(channelId))

  suspend fun setSelectedSource(channelId: String, url: String?) = db.channelDao().setSelectedSourceUrl(channelId, url)

  private class BuiltChannels(
    val channels: List<ChannelEntity>,
    val urlsByChannel: Map<String, List<StreamUrlEntity>>,
    val categories: List<CategoryEntity>,
    val countries: List<CountryEntity>,
  )

  // Everything is fetched before the database is touched, so a failed fetch leaves the old data intact.
  suspend fun refresh(onProgress: (RefreshProgress) -> Unit = {}): RefreshResult {
    onProgress(RefreshProgress.ChannelInfo)
    val categoryCsv = client.fetchCategoriesCsv()
    val countryCsv = client.fetchCountriesCsv()
    val channelCsv = client.fetchChannelsCsv()
    // Parsed up front so progress can count only playlist entries that will become channels.
    val channelRows = withContext(Dispatchers.Default) { parseCsv(channelCsv).associateBy { it.getValue("id") } }

    // Per-country files keep each channel's mirror URLs, but need the GitHub API to list them, which is
    // rate-limited per IP (403 on shared mobile networks). Fall back to the combined playlist, which needs no API.
    val playlists =
      runCatching {
          val codes = client.fetchPlaylistCountryCodes()
          // On Default, so the per-playlist scan runs off the main thread, on several threads at once.
          withContext(Dispatchers.Default) {
            val done = AtomicInteger()
            val found = Collections.synchronizedSet(HashSet<String>()) // ConcurrentHashMap.newKeySet() needs API 24
            onProgress(RefreshProgress.Playlists(0, codes.size, 0))
            client
              .fetchAllPlaylists(codes) { text ->
                for (match in TvgIdAttribute.findAll(text)) {
                  val tvgId = match.groupValues[1]
                  if (tvgId.substringBefore('@') in channelRows) found += tvgId
                }
                onProgress(RefreshProgress.Playlists(done.incrementAndGet(), codes.size, found.size))
              }
              .ifEmpty { error("no playlists") }
          }
        }
        .getOrElse {
          onProgress(RefreshProgress.CombinedPlaylist)
          listOf("combined" to client.fetchCombinedPlaylist())
        }
        .filter { it.second.isNotBlank() }
    check(playlists.isNotEmpty()) { "could not reach iptv-org (no playlists fetched)" }

    val existingSelections = db.channelDao().allSelectedSources().associate { it.id to it.selectedSourceUrl }

    // CPU-bound for ~11k channels: off the main thread.
    val built =
      withContext(Dispatchers.Default) {
        val categoryRows = parseCsv(categoryCsv)
        val countryRows = parseCsv(countryCsv)

        val entriesByKey = LinkedHashMap<String, MutableList<M3uEntry>>()
        for ((_, text) in playlists) {
          for (entry in parseM3u(text)) {
            entriesByKey.getOrPut(entry.tvgId) { mutableListOf() }.add(entry)
          }
        }

        var order = 0
        val newChannels = mutableListOf<ChannelEntity>()
        val urlsByChannel = mutableMapOf<String, List<StreamUrlEntity>>()
        for ((tvgId, entries) in entriesByKey) {
          val channelId = tvgId.substringBefore('@')
          val channelRow = channelRows[channelId] ?: continue
          val urls = entries.map { it.url }
          val preservedSelection = existingSelections[tvgId]?.takeIf { it in urls }
          newChannels +=
            ChannelEntity(
              id = tvgId,
              displayName = entries.first().title.ifBlank { channelRow["name"].orEmpty() },
              countryCode = channelRow["country"].orEmpty(),
              categoryIds = channelRow["categories"].orEmpty(),
              sortOrder = order++,
              selectedSourceUrl = preservedSelection,
            )
          urlsByChannel[tvgId] = entries.mapIndexed { idx, e -> StreamUrlEntity(channelId = tvgId, url = e.url, sortOrder = idx) }
        }
        // iptv-org defines categories and countries its playlists have no stream for ("XXX", Antarctica).
        val usedCategoryIds = newChannels.flatMapTo(HashSet()) { it.categoryIds.split(';') }
        val usedCountryCodes = newChannels.mapTo(HashSet()) { it.countryCode }
        BuiltChannels(
          channels = newChannels,
          urlsByChannel = urlsByChannel,
          categories =
            categoryRows
              .filter { it["id"] in usedCategoryIds }
              .mapIndexed { i, r -> CategoryEntity(r.getValue("id"), r.getValue("name"), i) },
          countries =
            countryRows
              .filter { it["code"] in usedCountryCodes }
              .mapIndexed { i, r -> CountryEntity(r.getValue("code"), r.getValue("name"), r.getValue("flag"), i) },
        )
      }

    // A 200 with a non-playlist body (captive portal, error page) parses to nothing; writing that would delete
    // every channel and, by cascade, every favourite. Keep the old catalogue instead.
    check(built.channels.isNotEmpty()) { "iptv-org returned no usable channels" }
    onProgress(RefreshProgress.Saving(built.channels.size))

    val existingIds = existingSelections.keys
    val newIds = built.channels.map { it.id }.toSet()
    val removedIds = (existingIds - newIds).toList()
    val addedCount = (newIds - existingIds).size
    val bookmarkedIds = db.bookmarkDao().allChannelIds().toSet()
    val bookmarksRemovedCount = removedIds.count { it in bookmarkedIds }

    db.withTransaction {
      db.categoryDao().replaceAll(built.categories)
      db.countryDao().replaceAll(built.countries)
      // Chunked: `IN (:ids)` binds one parameter per id. Cascades to stream_urls and bookmarks.
      for (chunk in removedIds.chunked(SqliteMaxBindVariables)) db.channelDao().deleteByIds(chunk)
      db.channelDao().upsertAll(built.channels)
      db.streamUrlDao().replaceAll(built.urlsByChannel)
      db.deletedChannelDao().clear()
      db.failedChannelDao().deleteOrphans()
    }

    return RefreshResult(total = built.channels.size, added = addedCount, removed = removedIds.size, bookmarksRemoved = bookmarksRemovedCount)
  }
}

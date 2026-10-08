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
import dev.meghsohor.meghtv.data.remote.ChannelList
import dev.meghsohor.meghtv.data.remote.ChannelListClient
import dev.meghsohor.meghtv.data.remote.ListManifest
import dev.meghsohor.meghtv.data.remote.parseChannelList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class RefreshResult(val total: Int, val added: Int, val removed: Int, val bookmarksRemoved: Int)

sealed interface RefreshProgress {
  data object Checking : RefreshProgress

  /** Uncompressed bytes, against the manifest's size. */
  data class Downloading(val bytes: Long, val total: Long) : RefreshProgress

  data class Saving(val channels: Int) : RefreshProgress
}

/** A downloaded list with new or changed channels, waiting for Update or Full refresh. Its removals are already applied. */
class ListUpdate internal constructor(
  val updatedAt: String,
  internal val built: BuiltChannels,
  val added: Int,
  val changed: Int,
  internal val urlsChangedIds: List<String>,
  internal val removed: Int,
  internal val bookmarksRemoved: Int,
) {
  val total: Int
    get() = built.channels.size
}

sealed interface ListCheck {
  data class UpToDate(val updatedAt: String) : ListCheck

  /** Nothing to ask about (only removals or menu changes), or nothing stored yet: applied already. */
  data class Applied(val result: RefreshResult) : ListCheck

  /** A background check found the list the user put off with Later. */
  data object Postponed : ListCheck

  data class Offer(val update: ListUpdate) : ListCheck
}

internal class BuiltChannels(
  val channels: List<ChannelEntity>,
  val urlsByChannel: Map<String, List<StreamUrlEntity>>,
  val categories: List<CategoryEntity>,
  val countries: List<CountryEntity>,
)

// Under SQLite's default limit of 999 bound parameters per statement.
private const val SqliteMaxBindVariables = 900

/** A list that would remove more than this share of the stored channels is refused: it points to a broken publish. */
private const val MaxRemovedShare = 0.5

/** [extraChannelsJson] reads the personal build's bundled channels (published list shape); null elsewhere. Called off the main thread. */
class MeghTVRepository(
  private val db: MeghTVDatabase,
  private val prefs: ChannelListPrefs,
  private val client: ChannelListClient = ChannelListClient(),
  private val extraChannelsJson: () -> String? = { null },
) {

  private val extraChannelsFile: String? by lazy { extraChannelsJson() }
  // Null for a file that doesn't parse: downloads then fail rather than drop its channels and their favourites.
  private val extraChannels: ChannelList? by lazy { extraChannelsFile?.let { runCatching { parseChannelList(it) }.getOrNull() } }

  /** The applied list's key: the published list, plus the bundled file when there is one, so a new file counts as a new list. */
  private suspend fun listKey(updatedAt: String): String {
    val file = withContext(Dispatchers.IO) { extraChannelsFile } ?: return updatedAt
    return "$updatedAt+${file.hashCode().toUInt().toString(16)}"
  }

  // An id the published list also has stays the published one.
  private fun ChannelList.withExtras(): ChannelList {
    if (extraChannelsFile == null) return this
    val extra = checkNotNull(extraChannels) { "the bundled channel file is broken" }
    val ids = channels.mapTo(HashSet()) { it.id }
    val categoryIds = categories.mapTo(HashSet()) { it.id }
    return ChannelList(categories + extra.categories.filter { it.id !in categoryIds }, countries, channels + extra.channels.filter { it.id !in ids })
  }

  val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
  val countries: Flow<List<CountryEntity>> = db.countryDao().observeAll()
  val allChannels: Flow<List<ChannelEntity>> = db.channelDao().observeAll()
  val bookmarkedChannels: Flow<List<ChannelEntity>> = db.channelDao().observeBookmarked()

  suspend fun allChannelIds(): List<String> = db.channelDao().allIds()

  suspend fun hasChannels(): Boolean = db.channelDao().hasAny()

  // What a list update does, for data stored before it did.
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

  fun isPostponed(updatedAt: String) = prefs.postponedUpdatedAt == updatedAt

  fun postpone(updatedAt: String) {
    prefs.postponedUpdatedAt = updatedAt
  }

  /**
   * Compares the published list with the device's own data, so the counts are right however many lists were skipped.
   * Removed channels go at once, so one can't stay playable while the user keeps putting the update off.
   */
  suspend fun checkForUpdate(background: Boolean, onProgress: (RefreshProgress) -> Unit = {}): ListCheck {
    onProgress(RefreshProgress.Checking)
    val manifest = client.fetchManifest()
    val key = listKey(manifest.updatedAt)
    if (!hasChannels()) return ListCheck.Applied(write(key, download(manifest, onProgress), full = true, urlsChangedIds = emptyList(), onProgress))
    if (key == prefs.appliedUpdatedAt) return ListCheck.UpToDate(manifest.updatedAt)
    // Its removals went in when it was first found.
    if (background && key == prefs.postponedUpdatedAt) return ListCheck.Postponed
    val built = download(manifest, onProgress)

    val stored = db.channelDao().getAll()
    val storedUrls = db.streamUrlDao().getAllUrls()
    val deletedIds = db.deletedChannelDao().allIds().toHashSet()
    val (added, changed, urlsChanged) =
      withContext(Dispatchers.Default) {
        val storedById = stored.associateBy { it.id }
        val urlsByChannel = storedUrls.groupBy({ it.channelId }, { it.url })
        var added = 0
        val changed = mutableListOf<String>()
        val urlsChanged = mutableListOf<String>()
        for (channel in built.channels) {
          val old = storedById[channel.id]
          if (old == null) {
            added++
            continue
          }
          val urlsDiffer = urlsByChannel[channel.id].orEmpty() != built.urlsByChannel[channel.id].orEmpty().map { it.url }
          if (urlsDiffer) urlsChanged += channel.id
          // A deleted channel's changes go in too, but there's nothing to show for them.
          val differs = urlsDiffer || old.displayName != channel.displayName || old.countryCode != channel.countryCode || old.categoryIds != channel.categoryIds
          if (differs && channel.id !in deletedIds) changed += channel.id
        }
        Triple(added, changed, urlsChanged)
      }
    val removedIds = stored.mapTo(HashSet()) { it.id } - built.channels.mapTo(HashSet()) { it.id }
    checkRemovals(removedIds.size, stored.size)
    if (added == 0 && changed.isEmpty()) return ListCheck.Applied(write(key, built, full = false, urlsChanged, onProgress))

    val bookmarkedIds = db.bookmarkDao().allChannelIds().toSet()
    db.withTransaction {
      // Chunked: `IN (:ids)` binds one parameter per id. Cascades to stream_urls and bookmarks.
      for (chunk in removedIds.chunked(SqliteMaxBindVariables)) db.channelDao().deleteByIds(chunk)
      db.failedChannelDao().deleteOrphans()
      db.categoryDao().deleteUnused()
      db.countryDao().deleteUnused()
    }
    return ListCheck.Offer(
      ListUpdate(key, built, added, changed.size, urlsChanged, removedIds.size, removedIds.count { it in bookmarkedIds })
    )
  }

  /** Update ([full] false) keeps deleted channels and the other channels' "not working" marks; Full refresh resets both. */
  suspend fun apply(update: ListUpdate, full: Boolean, onProgress: (RefreshProgress) -> Unit = {}): RefreshResult {
    val result = write(update.updatedAt, update.built, full, update.urlsChangedIds, onProgress)
    return result.copy(removed = result.removed + update.removed, bookmarksRemoved = result.bookmarksRemoved + update.bookmarksRemoved)
  }

  /** Downloads the list and applies it as a Full refresh: a new install, or Full refresh with no new list. */
  suspend fun fullRefresh(onProgress: (RefreshProgress) -> Unit = {}): RefreshResult {
    onProgress(RefreshProgress.Checking)
    val manifest = client.fetchManifest()
    return write(listKey(manifest.updatedAt), download(manifest, onProgress), full = true, urlsChangedIds = emptyList(), onProgress)
  }

  // Everything is downloaded before the database is touched, so a failed download leaves the old data intact.
  private suspend fun download(manifest: ListManifest, onProgress: (RefreshProgress) -> Unit): BuiltChannels {
    onProgress(RefreshProgress.Downloading(0, manifest.size))
    val list = client.fetchList(manifest) { onProgress(RefreshProgress.Downloading(it, manifest.size)) }
    val built = withContext(Dispatchers.Default) { list.withExtras().toEntities() }
    // Writing an empty list would delete every channel and, by cascade, every favourite.
    check(built.channels.isNotEmpty()) { "the channel list is empty" }
    return built
  }

  private suspend fun write(updatedAt: String, built: BuiltChannels, full: Boolean, urlsChangedIds: List<String>, onProgress: (RefreshProgress) -> Unit): RefreshResult {
    onProgress(RefreshProgress.Saving(built.channels.size))
    // Read inside the transaction, so a source picked meanwhile isn't overwritten with the old pick.
    val result = db.withTransaction {
      val existingSelections = db.channelDao().allSelectedSources().associate { it.id to it.selectedSourceUrl }
      val channels =
        built.channels.map { channel ->
          val selection = existingSelections[channel.id]?.takeIf { url -> built.urlsByChannel[channel.id].orEmpty().any { it.url == url } }
          channel.copy(selectedSourceUrl = selection)
        }
      val existingIds = existingSelections.keys
      val newIds = channels.mapTo(HashSet()) { it.id }
      val removedIds = (existingIds - newIds).toList()
      checkRemovals(removedIds.size, existingIds.size)
      val bookmarkedIds = db.bookmarkDao().allChannelIds().toSet()

      db.categoryDao().replaceAll(built.categories)
      db.countryDao().replaceAll(built.countries)
      for (chunk in removedIds.chunked(SqliteMaxBindVariables)) db.channelDao().deleteByIds(chunk)
      db.channelDao().upsertAll(channels)
      db.streamUrlDao().replaceAll(built.urlsByChannel)
      if (full) {
        db.deletedChannelDao().clear()
        db.failedChannelDao().clear()
      } else {
        // New sources deserve a fresh try.
        for (chunk in urlsChangedIds.chunked(SqliteMaxBindVariables)) db.failedChannelDao().removeIds(chunk)
      }
      db.failedChannelDao().deleteOrphans()
      RefreshResult(
        total = channels.size,
        added = (newIds - existingIds).size,
        removed = removedIds.size,
        bookmarksRemoved = removedIds.count { it in bookmarkedIds },
      )
    }
    prefs.appliedUpdatedAt = updatedAt
    prefs.postponedUpdatedAt = null
    return result
  }
}

private fun checkRemovals(removed: Int, stored: Int) =
  check(stored == 0 || removed <= stored * MaxRemovedShare) { "The new channel list is missing most channels, so the current one was kept." }

private fun ChannelList.toEntities(): BuiltChannels {
  val channelEntities = mutableListOf<ChannelEntity>()
  val urlsByChannel = LinkedHashMap<String, List<StreamUrlEntity>>()
  for (channel in channels) {
    val urls = channel.urls.distinct()
    if (urls.isEmpty() || channel.id in urlsByChannel) continue
    channelEntities +=
      ChannelEntity(
        id = channel.id,
        displayName = channel.name,
        countryCode = channel.country,
        categoryIds = channel.categories.joinToString(";"),
        sortOrder = channelEntities.size,
      )
    urlsByChannel[channel.id] = urls.mapIndexed { i, url -> StreamUrlEntity(channelId = channel.id, url = url, sortOrder = i) }
  }
  return BuiltChannels(
    channels = channelEntities,
    urlsByChannel = urlsByChannel,
    categories = categories.mapIndexed { i, c -> CategoryEntity(c.id, c.name, i) },
    countries = countries.mapIndexed { i, c -> CountryEntity(c.code, c.name, c.flag, i) },
  )
}

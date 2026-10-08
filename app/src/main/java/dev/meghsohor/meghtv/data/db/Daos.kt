package dev.meghsohor.meghtv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
  @Query("SELECT * FROM categories ORDER BY sortOrder") fun observeAll(): Flow<List<CategoryEntity>>

  @Query("DELETE FROM categories") suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(categories: List<CategoryEntity>)

  @Query(
    "DELETE FROM categories WHERE NOT EXISTS (SELECT 1 FROM channels WHERE (';' || channels.categoryIds || ';') LIKE ('%;' || categories.id || ';%'))"
  )
  suspend fun deleteUnused()

  @Transaction
  suspend fun replaceAll(categories: List<CategoryEntity>) {
    deleteAll()
    insertAll(categories)
  }
}

@Dao
interface CountryDao {
  // Leaves out a country whose channels were all deleted.
  @Query(
    "SELECT * FROM countries WHERE code IN (SELECT countryCode FROM channels WHERE id NOT IN (SELECT channelId FROM deleted_channels)) ORDER BY sortOrder"
  )
  fun observeAll(): Flow<List<CountryEntity>>

  @Query("DELETE FROM countries") suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(countries: List<CountryEntity>)

  @Query("DELETE FROM countries WHERE code NOT IN (SELECT DISTINCT countryCode FROM channels)") suspend fun deleteUnused()

  @Transaction
  suspend fun replaceAll(countries: List<CountryEntity>) {
    deleteAll()
    insertAll(countries)
  }
}

data class ChannelSelection(val id: String, val selectedSourceUrl: String?)

// Lists sort by name (source order reads as random) and leave out deleted channels. A channel with no country
// belongs only to its exclusive categories, so All Channels and Search leave it out too.
@Dao
interface ChannelDao {
  @Query("SELECT * FROM channels WHERE countryCode != '' AND id NOT IN (SELECT channelId FROM deleted_channels) ORDER BY displayName COLLATE NOCASE, sortOrder")
  fun observeAll(): Flow<List<ChannelEntity>>

  @Query(
    "SELECT * FROM channels WHERE (';' || categoryIds || ';') LIKE ('%;' || :categoryId || ';%') AND id NOT IN (SELECT channelId FROM deleted_channels) ORDER BY displayName COLLATE NOCASE, sortOrder"
  )
  fun observeByCategory(categoryId: String): Flow<List<ChannelEntity>>

  @Query("SELECT * FROM channels WHERE countryCode = :countryCode AND id NOT IN (SELECT channelId FROM deleted_channels) ORDER BY displayName COLLATE NOCASE, sortOrder")
  fun observeByCountry(countryCode: String): Flow<List<ChannelEntity>>

  @Query("SELECT * FROM channels WHERE displayName LIKE '%' || :query || '%' AND countryCode != '' AND id NOT IN (SELECT channelId FROM deleted_channels) ORDER BY displayName COLLATE NOCASE, sortOrder")
  fun observeSearch(query: String): Flow<List<ChannelEntity>>

  @Query(
    "SELECT channels.* FROM channels INNER JOIN bookmarks ON channels.id = bookmarks.channelId WHERE channels.id NOT IN (SELECT channelId FROM deleted_channels) ORDER BY bookmarks.addedAt DESC"
  )
  fun observeBookmarked(): Flow<List<ChannelEntity>>

  @Query("SELECT id FROM channels") suspend fun allIds(): List<String>

  @Query("SELECT * FROM channels") suspend fun getAll(): List<ChannelEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM channels)") suspend fun hasAny(): Boolean

  @Query("SELECT * FROM channels WHERE id = :id") suspend fun getById(id: String): ChannelEntity?

  @Query("SELECT * FROM channels WHERE id = :id") fun observeById(id: String): Flow<ChannelEntity?>

  @Query("SELECT id, selectedSourceUrl FROM channels") suspend fun allSelectedSources(): List<ChannelSelection>

  // Not @Insert(REPLACE): that deletes and reinserts, and the delete cascades to bookmarks.
  @Upsert suspend fun upsertAll(channels: List<ChannelEntity>)

  @Query("DELETE FROM channels WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)

  @Query("UPDATE channels SET selectedSourceUrl = :url WHERE id = :channelId")
  suspend fun setSelectedSourceUrl(channelId: String, url: String?)
}

@Dao
interface StreamUrlDao {
  @Query("SELECT * FROM stream_urls WHERE channelId = :channelId ORDER BY sortOrder")
  fun observeForChannel(channelId: String): Flow<List<StreamUrlEntity>>

  @Query("SELECT * FROM stream_urls WHERE channelId = :channelId ORDER BY sortOrder")
  suspend fun getForChannel(channelId: String): List<StreamUrlEntity>

  @Query("SELECT * FROM stream_urls ORDER BY channelId, sortOrder") suspend fun getAllUrls(): List<StreamUrlEntity>

  @Query("DELETE FROM stream_urls") suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(urls: List<StreamUrlEntity>)

  // The whole table: a per-channel `IN (...)` delete over ~11k ids exceeds SQLite's bind-parameter limit.
  @Transaction
  suspend fun replaceAll(urlsByChannel: Map<String, List<StreamUrlEntity>>) {
    deleteAll()
    insertAll(urlsByChannel.values.flatten())
  }
}

@Dao
interface BookmarkDao {
  @Query("SELECT channelId FROM bookmarks") suspend fun allChannelIds(): List<String>

  @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE channelId = :channelId)")
  fun observeIsBookmarked(channelId: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(bookmark: BookmarkEntity)

  @Query("DELETE FROM bookmarks WHERE channelId = :channelId") suspend fun remove(channelId: String)
}

@Dao
interface DeletedChannelDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(entry: DeletedChannelEntity)

  @Query("SELECT channelId FROM deleted_channels") suspend fun allIds(): List<String>

  @Query("DELETE FROM deleted_channels") suspend fun clear()
}

@Dao
interface FailedChannelDao {
  @Query("SELECT channelId FROM failed_channels") fun observeIds(): Flow<List<String>>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(entry: FailedChannelEntity)

  @Query("DELETE FROM failed_channels WHERE channelId = :channelId") suspend fun remove(channelId: String)

  @Query("DELETE FROM failed_channels WHERE channelId IN (:channelIds)") suspend fun removeIds(channelIds: List<String>)

  @Query("DELETE FROM failed_channels") suspend fun clear()

  @Query("DELETE FROM failed_channels WHERE channelId NOT IN (SELECT id FROM channels)") suspend fun deleteOrphans()
}

package dev.meghsohor.meghtv.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val sortOrder: Int)

@Entity(tableName = "countries")
data class CountryEntity(@PrimaryKey val code: String, val name: String, val flag: String, val sortOrder: Int)

/** One row per channel feed, id "channelId@feedId". Its mirror URLs are [StreamUrlEntity] rows. */
@Entity(
  tableName = "channels",
  indices = [Index("countryCode"), Index("categoryIds")],
)
data class ChannelEntity(
  @PrimaryKey val id: String,
  val displayName: String,
  val countryCode: String,
  /** Semicolon-joined, as in iptv-org's channels.csv. */
  val categoryIds: String,
  val sortOrder: Int,
  /** A URL picked by hand, tried first; null for the stored order. */
  val selectedSourceUrl: String? = null,
)

@Entity(
  tableName = "stream_urls",
  primaryKeys = ["channelId", "sortOrder"],
  foreignKeys = [
    ForeignKey(
      entity = ChannelEntity::class,
      parentColumns = ["id"],
      childColumns = ["channelId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("channelId")],
)
data class StreamUrlEntity(val channelId: String, val url: String, val sortOrder: Int)

@Entity(
  tableName = "bookmarks",
  foreignKeys = [
    ForeignKey(
      entity = ChannelEntity::class,
      parentColumns = ["id"],
      childColumns = ["channelId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
)
data class BookmarkEntity(@PrimaryKey val channelId: String, val addedAt: Long)

/** Every source failed the last time it played. */
@Entity(tableName = "failed_channels")
data class FailedChannelEntity(@PrimaryKey val channelId: String, val failedAt: Long)

/** Hidden from every list until a Full refresh; the channel row stays, so its favourite does too. */
@Entity(tableName = "deleted_channels")
data class DeletedChannelEntity(@PrimaryKey val channelId: String)

package com.magen.player.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.magen.player.database.converters.NetworkProtocolConverter
import com.magen.player.database.dao.BookmarkDao
import com.magen.player.database.dao.HybridMediaDao
import com.magen.player.database.dao.NetworkConnectionDao
import com.magen.player.database.dao.PlaybackStateDao
import com.magen.player.database.dao.PlaylistDao
import com.magen.player.database.dao.RecentlyPlayedDao
import com.magen.player.database.dao.VideoMetadataDao
import com.magen.player.database.dao.ShortsMediaDao
import com.magen.player.database.entities.BookmarkEntity
import com.magen.player.database.entities.HybridMediaEntity
import com.magen.player.database.entities.HybridMediaRootEntity
import com.magen.player.database.entities.PlaybackStateEntity
import com.magen.player.database.entities.PlaylistEntity
import com.magen.player.database.entities.PlaylistItemEntity
import com.magen.player.database.entities.RecentlyPlayedEntity
import com.magen.player.database.dao.MediaPlayCountDao
import com.magen.player.database.entities.MediaPlayCountEntity
import com.magen.player.database.entities.ShortsMediaEntity
import com.magen.player.database.entities.VideoMetadataEntity
import com.magen.player.domain.network.NetworkConnection

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    VideoMetadataEntity::class,
    NetworkConnection::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
    ShortsMediaEntity::class,
    HybridMediaEntity::class,
    HybridMediaRootEntity::class,
    MediaPlayCountEntity::class,
    BookmarkEntity::class,
  ],
  version = 19,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class)
abstract class MagenPlayerDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun playlistDao(): PlaylistDao

  abstract fun shortsMediaDao(): ShortsMediaDao

  abstract fun hybridMediaDao(): HybridMediaDao

  abstract fun mediaPlayCountDao(): MediaPlayCountDao

  abstract fun bookmarkDao(): BookmarkDao
}

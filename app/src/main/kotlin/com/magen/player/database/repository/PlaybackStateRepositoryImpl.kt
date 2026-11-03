package com.magen.player.database.repository

import com.magen.player.database.entities.PlaybackStateEntity
import com.magen.player.database.MagenPlayerDatabase
import com.magen.player.domain.playbackstate.repository.PlaybackStateRepository

class PlaybackStateRepositoryImpl(
  private val database: MagenPlayerDatabase,
) : PlaybackStateRepository {
  override suspend fun upsert(playbackState: PlaybackStateEntity) {
    database.videoDataDao().upsert(playbackState)
  }

  override suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity? =
    database.videoDataDao().getVideoDataByTitle(mediaTitle)

  override suspend fun clearAllPlaybackStates() {
    database.videoDataDao().clearAllPlaybackStates()
  }

  override suspend fun deleteByTitle(mediaTitle: String) {
    database.videoDataDao().deleteByTitle(mediaTitle)
  }

  override suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  ) {
    database.videoDataDao().updateMediaTitle(oldTitle, newTitle)
  }

  override suspend fun getAllPlaybackStates(): List<PlaybackStateEntity> =
    database.videoDataDao().getAllPlaybackStates()

  override fun observeAllPlaybackStates(): kotlinx.coroutines.flow.Flow<List<PlaybackStateEntity>> =
    database.videoDataDao().observeAllPlaybackStates()
}

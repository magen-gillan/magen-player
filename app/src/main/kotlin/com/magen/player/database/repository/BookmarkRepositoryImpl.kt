package com.magen.player.database.repository

import kotlinx.coroutines.flow.Flow
import com.magen.player.database.MagenPlayerDatabase
import com.magen.player.database.dao.BookmarkDao
import com.magen.player.database.entities.BookmarkEntity
import com.magen.player.domain.bookmark.repository.BookmarkRepository

class BookmarkRepositoryImpl(
  private val dao: BookmarkDao,
) : BookmarkRepository {
  constructor(database: MagenPlayerDatabase) : this(database.bookmarkDao())

  override fun observeByMediaKey(mediaKey: String): Flow<List<BookmarkEntity>> = dao.observeByMediaKey(mediaKey)

  override suspend fun getByMediaKey(mediaKey: String): List<BookmarkEntity> = dao.getByMediaKey(mediaKey)

  override suspend fun add(
    mediaKey: String,
    positionMs: Long,
    title: String,
  ): Long {
    val position = positionMs.coerceAtLeast(0L)
    // A unique index on (mediaKey, positionMs) keeps repeated "+ bookmark" presses idempotent.
    if (dao.getByMediaKey(mediaKey).any { it.positionMs == position }) return 0L
    return dao.upsert(
      BookmarkEntity(
        mediaKey = mediaKey,
        positionMs = position,
        title = title,
        createdAt = System.currentTimeMillis(),
      ),
    )
  }

  override suspend fun rename(
    id: Long,
    title: String,
  ) = dao.rename(id, title)

  override suspend fun delete(id: Long) = dao.deleteById(id)

  override suspend fun updateMediaKey(
    oldKey: String,
    newKey: String,
  ) = dao.updateMediaKey(oldKey, newKey)
}

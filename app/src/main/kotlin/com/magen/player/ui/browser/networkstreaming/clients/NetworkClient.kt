package com.magen.player.ui.browser.networkstreaming.clients

import android.net.Uri
import com.magen.player.domain.network.NetworkConnection
import com.magen.player.domain.network.NetworkFile
import java.io.InputStream

/**
 * Common interface for all network protocol clients
 */
interface NetworkClient {
  val connection: NetworkConnection

  /**
   * Connect to the server
   */
  suspend fun connect(): Result<Unit>

  /**
   * Disconnect from the server
   */
  suspend fun disconnect()

  /**
   * Check if currently connected
   */
  fun isConnected(): Boolean

  /**
   * List files and directories at the given path
   */
  suspend fun listFiles(path: String): Result<List<NetworkFile>>

  /**
   * Get input stream for a file
   */
  suspend fun getFileStream(path: String): Result<InputStream>

  /**
   * Get file URI for playback
   */
  suspend fun getFileUri(path: String): Result<Uri>

  /**
   * Get file size in bytes
   */
  suspend fun getFileSize(path: String): Result<Long>
}

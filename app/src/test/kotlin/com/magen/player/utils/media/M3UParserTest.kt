package com.magen.player.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3UParserTest {

  @Test
  fun parseContent_localM3u_returnsLocalItemsAndMetadata() {
    val m3uContent = """
      #EXTM3U
      #EXTINF:123 group-title="Movies",Sample Movie
      /storage/emulated/0/Movies/Sample.Movie.mp4
      #EXTINF:-1,Sample Clip
      file:///storage/emulated/0/Movies/clip.mkv
    """.trimIndent()

    val result = M3UParser.parseContent(m3uContent, "sample_playlist.m3u")

    assertTrue(result is M3UParseResult.Success)
    val success = result as M3UParseResult.Success
    assertEquals("sample playlist", success.playlistName)
    assertEquals(2, success.items.size)
    assertEquals("/storage/emulated/0/Movies/Sample.Movie.mp4", success.items[0].path)
    assertEquals("Sample Movie", success.items[0].title)
    assertEquals(123, success.items[0].duration)
    assertEquals("file:///storage/emulated/0/Movies/clip.mkv", success.items[1].path)
    assertEquals("Sample Clip", success.items[1].title)
    assertEquals(-1, success.items[1].duration)
  }

  @Test
  fun parseContent_ignoresNetworkEntriesAndKeepsLocalItems() {
    val content = """
      #EXTM3U
      #EXTINF:60,Remote video
      https://example.invalid/video.mp4
      #EXTINF:60,Local video
      /storage/emulated/0/Movies/local.mp4
    """.trimIndent()

    val result = M3UParser.parseContent(content)

    assertTrue(result is M3UParseResult.Success)
    val success = result as M3UParseResult.Success
    assertEquals(1, success.items.size)
    assertEquals("/storage/emulated/0/Movies/local.mp4", success.items.single().path)
    assertEquals("Local video", success.items.single().title)
  }

  @Test
  fun parseContent_relativePathsResolveAgainstLocalPlaylistDirectory() {
    val content = """
      #EXTM3U
      #EXTINF:60,Relative Video
      episodes/video2.mp4
    """.trimIndent()

    val result = M3UParser.parseContent(
      content,
      sourceName = "list.m3u",
      baseDirectory = "/storage/emulated/0/Movies",
    )

    assertTrue(result is M3UParseResult.Success)
    val success = result as M3UParseResult.Success
    assertEquals(1, success.items.size)
    assertEquals("/storage/emulated/0/Movies/episodes/video2.mp4", success.items.single().path)
  }

  @Test
  fun parseContent_hlsPlaylist_returnsError() {
    val hlsContent = """
      #EXTM3U
      #EXT-X-VERSION:3
      #EXT-X-TARGETDURATION:10
      #EXTINF:10.0,
      segment1.ts
    """.trimIndent()

    val result = M3UParser.parseContent(hlsContent)

    assertTrue(result is M3UParseResult.Error)
    assertEquals("HLS stream playlist", (result as M3UParseResult.Error).message)
  }

  @Test
  fun parseContent_emptyPlaylist_returnsError() {
    val result = M3UParser.parseContent("")

    assertTrue(result is M3UParseResult.Error)
    assertEquals("Playlist is empty", (result as M3UParseResult.Error).message)
  }
}

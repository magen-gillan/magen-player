package com.magen.player.ui.browser.filesystem

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import com.magen.player.database.repository.VideoMetadataCacheRepository
import com.magen.player.preferences.FolderSortType
import com.magen.player.preferences.SortOrder
import com.magen.player.domain.pinned.PinnedItemRepository
import com.magen.player.domain.playbackstate.repository.PlaybackStateRepository
import com.magen.player.domain.recentlyplayed.repository.RecentlyPlayedRepository
import com.magen.player.preferences.AdvancedPreferences
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.preferences.FoldersPreferences
import com.magen.player.preferences.UiPreferences
import com.magen.player.utils.storage.MediaTreeStore

@OptIn(ExperimentalCoroutinesApi::class)
class FileSystemBrowserViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val application = mockk<Application>(relaxed = true)

  private val foldersPreferences = mockk<FoldersPreferences>(relaxed = true)
  private val appearancePreferences = mockk<AppearancePreferences>(relaxed = true)
  private val pinnedItemRepository = mockk<PinnedItemRepository>(relaxed = true)
  private val treeStore = mockk<MediaTreeStore>(relaxed = true)
  private val metadataCache = mockk<VideoMetadataCacheRepository>(relaxed = true)
  private val uiPreferences = mockk<UiPreferences>(relaxed = true)
  private val playbackStateRepository = mockk<PlaybackStateRepository>(relaxed = true)
  private val browserPreferences = mockk<BrowserPreferences>(relaxed = true)
  private val recentlyPlayedRepository = mockk<RecentlyPlayedRepository>(relaxed = true)
  private val advancedPreferences = mockk<AdvancedPreferences>(relaxed = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { pinnedItemRepository.pinnedPaths(any()) } returns MutableStateFlow(emptyList())
    every { pinnedItemRepository.getPinnedPaths(any()) } returns emptyList()

    every { browserPreferences.folderSortType.changes() } returns MutableStateFlow(FolderSortType.Title)
    every { browserPreferences.folderSortOrder.changes() } returns MutableStateFlow(SortOrder.Ascending)
    every { browserPreferences.showAudioFiles.changes() } returns MutableStateFlow(false)
    every { browserPreferences.showAudioFiles.get() } returns false
    every { browserPreferences.flattenFolders.changes() } returns MutableStateFlow(true)
    every { browserPreferences.flattenFolders.get() } returns true
    every { foldersPreferences.blacklistedFolders.changes() } returns MutableStateFlow(emptySet())
    every { foldersPreferences.blacklistedFolders.get() } returns emptySet()

    stopKoin()
    startKoin {
      modules(
        module {
          single<android.content.Context> { application }
          single { foldersPreferences }
          single { appearancePreferences }
          single { pinnedItemRepository }
          single { treeStore }
          single { metadataCache }
          single { uiPreferences }
          single { playbackStateRepository }
          single { browserPreferences }
          single { recentlyPlayedRepository }
          single { advancedPreferences }
        },
      )
    }
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  @Test
  fun `root browser initializes at root with empty breadcrumbs`() {
    val viewModel = FileSystemBrowserViewModel(application, initialPath = null)

    assertTrue("isAtRoot must be true when initialPath is null", viewModel.isAtRoot.value)
    assertTrue("breadcrumbs must be empty when initialPath is null", viewModel.breadcrumbs.value.isEmpty())
  }

  @Test
  fun `subfolder browser initializes not at root with path breadcrumbs`() {
    val subfolderPath = "/storage/emulated/0/Movies"
    val viewModel = FileSystemBrowserViewModel(application, initialPath = subfolderPath)

    assertFalse("isAtRoot must be false when browsing a subfolder", viewModel.isAtRoot.value)
    assertFalse("breadcrumbs must not be empty when browsing a subfolder", viewModel.breadcrumbs.value.isEmpty())
    assertEquals("Movies", viewModel.breadcrumbs.value.last().name)
  }
}

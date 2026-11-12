package com.magen.player.di

import com.magen.player.domain.anime4k.Anime4KManager
import com.magen.player.domain.hdr.HdrToysManager
import com.magen.player.ui.browser.miniplayer.MiniPlayerStateManager
import com.magen.player.ui.player.HeadlessPlaybackController
import com.magen.player.ui.player.managers.PlaybackManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val domainModule = module {
  single { Anime4KManager(androidContext()) }
  single { HdrToysManager(androidContext()) }
  single { PlaybackManager(get()) }
  single { MiniPlayerStateManager() }
  single { HeadlessPlaybackController(androidContext()) }
}

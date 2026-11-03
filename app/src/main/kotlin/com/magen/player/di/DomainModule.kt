package com.magen.player.di

import com.magen.player.domain.anime4k.Anime4KManager
import com.magen.player.domain.hdr.HdrToysManager
import com.magen.player.repository.wyzie.WyzieSearchRepository
import com.magen.player.repository.subtitle.OpenAiSubtitleTranslator
import com.magen.player.ui.player.managers.PlaybackManager
import com.magen.player.ui.player.HeadlessPlaybackController
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import java.util.concurrent.TimeUnit

import com.magen.player.ui.browser.miniplayer.MiniPlayerStateManager

val domainModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    single { Anime4KManager(androidContext()) }
    single { HdrToysManager(androidContext()) }
    single { WyzieSearchRepository(androidContext(), get(), get(), get(), get()) }
    single { OpenAiSubtitleTranslator(androidContext(), get(), get()) }
    single { PlaybackManager(get()) }
    single { MiniPlayerStateManager() }
    single { HeadlessPlaybackController(androidContext()) }
}



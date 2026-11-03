package com.magen.player.di

import com.magen.player.database.MagenPlayerDatabase
import com.magen.player.domain.pinned.PinnedItemRepository
import com.magen.player.domain.pinned.PinnedItemRepositoryImpl
import com.magen.player.preferences.AdvancedPreferences
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.preferences.AudioPreferences
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.preferences.DecoderPreferences
import com.magen.player.preferences.FoldersPreferences
import com.magen.player.preferences.GesturePreferences
import com.magen.player.preferences.PlayerPreferences
import com.magen.player.preferences.SettingsManager
import com.magen.player.preferences.SubtitlesPreferences
import com.magen.player.preferences.UiPreferences
import com.magen.player.preferences.YtdlPreferences
import com.magen.player.ui.player.PlayerTutorialManager
import com.magen.player.jellyfin.preferences.JellyfinPreferences
import com.magen.player.preferences.preference.AndroidPreferenceStore
import com.magen.player.preferences.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule =
  module {
    single { JellyfinPreferences(androidContext()) }
    single { AndroidPreferenceStore(androidContext()) }.bind(PreferenceStore::class)

    single { AppearancePreferences(get()) }
    singleOf(::PlayerPreferences)
    singleOf(::GesturePreferences)
    singleOf(::DecoderPreferences)
    singleOf(::SubtitlesPreferences)
    singleOf(::AudioPreferences)
    singleOf(::AdvancedPreferences)
    single { BrowserPreferences(get(), androidContext()) }
    singleOf(::FoldersPreferences)
    single<PinnedItemRepository> { PinnedItemRepositoryImpl(get()) }
    singleOf(::SettingsManager)
    singleOf(::UiPreferences)
    singleOf(::PlayerTutorialManager)
    singleOf(::YtdlPreferences)
  }

package com.magen.player.ui.welcome

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.magen.player.BuildConfig
import com.magen.player.R
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.presentation.Screen
import com.magen.player.ui.browser.MainScreen
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.utils.permission.PermissionUtils
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/**
 * Magen Player welcome (same route object as before, so MainActivity is untouched).
 * Permissions: video/storage access (existing PermissionUtils rules) and notifications.
 * The in-screen language picker was removed: the app follows the device language.
 */
@Serializable
object WelcomeScreen : Screen {

  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()

    val storage = remember {
      WelcomePermission(
        glyph = "▶",
        title = R.string.mwelcome_perm_videos_title,
        desc = R.string.mwelcome_perm_videos_desc,
        isGranted = { c -> PermissionUtils.isStoragePermissionGranted(c) },
        request = { c ->
          if (BuildConfig.SCOPED_STORAGE_ONLY || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            PermissionRequest.Runtime(listOf(PermissionUtils.getStoragePermission()))
          } else {
            val pkg = Uri.parse("package:${c.packageName}")
            PermissionRequest.Settings(
              listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
              )
            )
          }
        },
      )
    }

    fun navigateToMain() {
      appearancePreferences.onboardingCompleted.set(true)
      if (backstack.lastOrNull() == WelcomeScreen) {
        backstack.add(MainScreen)
        backstack.remove(WelcomeScreen)
      } else if (backstack.none { it == MainScreen }) {
        backstack.add(MainScreen)
      }
    }

    MagenWelcomeScreen(
        brandName = R.string.mwelcome_brand,
        tagline = R.string.mwelcome_tag,
        helloTitle = R.string.mwelcome_hi_t,
        helloBody = R.string.mwelcome_hi_b,
        logo = R.drawable.welcome_brand_mark,
        accent = Color(0xFF5BBCFF),
        permissions = remember { listOf(storage, MagenPermissions.notifications()) },
        onFinished = { navigateToMain() },
    )
  }
}

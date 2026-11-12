package com.magen.player

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.preferences.preference.collectAsState
import com.magen.player.presentation.Screen
import com.magen.player.ui.browser.MainScreen
import com.magen.player.ui.theme.DarkMode
import com.magen.player.ui.theme.MagenPlayerTheme
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.ui.utils.navigateBack
import com.magen.player.utils.permission.PermissionUtils
import com.magen.player.ui.browser.miniplayer.MiniPlayer
import com.magen.player.ui.browser.miniplayer.MiniPlayerDefaults
import com.magen.player.ui.browser.miniplayer.MiniPlayerStateManager
import com.magen.player.ui.browser.LocalNavigationBarHeight
import com.magen.player.ui.welcome.WelcomeScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.android.ext.android.inject

/**
 * Main entry point for the application
 */
class MainActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()
  private val miniPlayerStateManager by inject<MiniPlayerStateManager>()
  
  // Register the ActivityResultLauncher at class level
  private val mediaAccessLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    PermissionUtils.handleMediaAccessResult(result.resultCode)
  }

  override fun attachBaseContext(newBase: android.content.Context) {
    super.attachBaseContext(com.magen.player.utils.locale.LocaleHelper.wrapContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    
    PermissionUtils.setMediaAccessLauncher(mediaAccessLauncher)

    setContent {
      // Set up theme and edge-to-edge display
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      val isDarkMode = dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)
      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.Transparent.toArgb(),
        ) { isDarkMode },
      )

      MagenPlayerTheme {
        Surface {
          Navigator()
        }
      }
    }
  }

  override fun onDestroy() {
    try {
      super.onDestroy()
    } catch (e: Exception) {
      Log.e("MainActivity", "Error during onDestroy", e)
    }
  }
  /**
   * Navigator that handles screen transitions and provides shared states
   */
  @Composable
  fun Navigator() {
    val context = LocalContext.current
    val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
    val initialScreen = remember {
      if (hasCompletedOnboarding) {
        MainScreen
      } else {
        WelcomeScreen
      }
    }
    val backstack = rememberNavBackStack(initialScreen)

    @Suppress("UNCHECKED_CAST")
    val typedBackstack = backstack as NavBackStack<Screen>
    val miniPlayerState by miniPlayerStateManager.state.collectAsState()
    val hideNavigationBar by MainScreen.shouldHideNavigationBar.collectAsState()
    val currentRoute = typedBackstack.lastOrNull()
    val isMainScreen = currentRoute == MainScreen
    val isWelcomeScreen = currentRoute == WelcomeScreen
    
    val targetBottomPadding = if (isMainScreen && !hideNavigationBar) {
      if (miniPlayerState.isExpanded) 8.dp else 72.dp
    } else 8.dp
    val animatedBottomPadding by androidx.compose.animation.core.animateDpAsState(
      targetValue = targetBottomPadding,
      animationSpec = tween(220),
      label = "miniPlayerBottomPadding"
    )

    val targetMiniPlayerHeight = if (miniPlayerState.isPlaybackActive) MiniPlayerDefaults.CompactHeight else 0.dp
    val miniPlayerHeight by androidx.compose.animation.core.animateDpAsState(
      targetValue = targetMiniPlayerHeight,
      animationSpec = tween(220),
      label = "miniPlayerHeight"
    )
    val navBarHeight = if (isMainScreen && !hideNavigationBar) 64.dp else 0.dp
    val totalNavigationBarHeight = navBarHeight + miniPlayerHeight

    // System back: pop one entry while a screen is on top of the root, otherwise send the
    // task to the background. Popping the root entry is never allowed, because NavDisplay
    // throws "backstack cannot be empty".
    BackHandler(enabled = typedBackstack.size > 1 || isMainScreen || isWelcomeScreen) {
      if (!typedBackstack.navigateBack()) {
        (context as? Activity)?.moveTaskToBack(true)
      }
    }

    // Provide shared states to all screens
    CompositionLocalProvider(
      LocalBackStack provides typedBackstack,
      LocalNavigationBarHeight provides totalNavigationBarHeight
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
          backStack = typedBackstack,
          onBack = { typedBackstack.navigateBack() },
          entryProvider = { route -> NavEntry(route) { route.Content() } },
          popTransitionSpec = {
            (
              fadeIn(animationSpec = tween(220)) +
                slideIn(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
            ) togetherWith (
                fadeOut(animationSpec = tween(220)) +
                  slideOut(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
            )
          },
          transitionSpec = {
            (
              fadeIn(animationSpec = tween(220)) +
                slideIn(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
            ) togetherWith (
                fadeOut(animationSpec = tween(220)) +
                  slideOut(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
            )
          },
          predictivePopTransitionSpec = {
            (
              fadeIn(animationSpec = tween(220)) +
                scaleIn(
                  animationSpec = tween(220, delayMillis = 30),
                  initialScale = .9f,
                  TransformOrigin(-1f, .5f),
                )
            ) togetherWith (
                fadeOut(animationSpec = tween(220)) +
                  scaleOut(
                    animationSpec = tween(220, delayMillis = 30),
                    targetScale = .9f,
                    TransformOrigin(-1f, .5f),
                  )
            )
          },
        )

        MiniPlayer(
          stateManager = miniPlayerStateManager,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = animatedBottomPadding)
        )
      }
    }
  }
}

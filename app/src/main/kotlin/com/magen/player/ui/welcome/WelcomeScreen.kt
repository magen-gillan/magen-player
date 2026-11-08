package com.magen.player.ui.welcome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import com.magen.player.R
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.presentation.Screen
import com.magen.player.ui.browser.MainScreen
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.utils.locale.LocaleHelper
import com.magen.player.utils.permission.PermissionUtils

/**
 * Welcome screen following the "MagenPlayer – Welcome screen" design.
 *
 * Fixed dark palette (independent of the app theme, like the reference design):
 * bg #0A1020, radial highlight #17315F, cards #121A2E, borders #22304F,
 * text #EEF3FF, muted #9AACCB, accent #6EA2FF.
 */
@Serializable
object WelcomeScreen : Screen {

  // Design tokens from the HTML reference
  private val Bg = Color(0xFF0A1020)
  private val BgHighlight = Color(0xFF17315F)
  private val Card = Color(0xFF121A2E)
  private val Line = Color(0xFF22304F)
  private val TextPrimary = Color(0xFFEEF3FF)
  private val TextMuted = Color(0xFF9AACCB)
  private val Accent = Color(0xFF6EA2FF)
  private val NoteBg = Color(0xFF1B2745)
  private val IconCircle = Color(0xFF1F2C4B)
  private val GoStart = Color(0xFFA9C4FF)
  private val GoEnd = Color(0xFF7FA8FF)

  @Composable
  override fun Content() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()
    val coroutineScope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }

    var isGranted by remember {
      mutableStateOf(PermissionUtils.hasFullStorageAccess(context))
    }

    // Re-check permission status when app resumes from system permission screens
    DisposableEffect(Unit) {
      val lifecycleOwner = context as? LifecycleOwner
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          isGranted = PermissionUtils.hasFullStorageAccess(context)
        }
      }
      lifecycleOwner?.lifecycle?.addObserver(observer)
      onDispose {
        lifecycleOwner?.lifecycle?.removeObserver(observer)
      }
    }

    fun navigateToMain() {
      appearancePreferences.onboardingCompleted.set(true)
      if (backstack.lastOrNull() == WelcomeScreen) {
        // Add the destination before removing the current entry so the back stack is never
        // empty (NavDisplay rejects an empty back stack during recomposition).
        backstack.add(MainScreen)
        backstack.remove(WelcomeScreen)
      } else if (backstack.none { it == MainScreen }) {
        // Onboarding finished while this entry is no longer on top; make sure a root exists.
        backstack.add(MainScreen)
      }
    }

    // One-shot request of EVERY runtime permission the app needs (media + notifications).
    val permissionsLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
      isGranted = PermissionUtils.hasFullStorageAccess(context)
      when {
        // Non-scoped Android 11+ builds: a scoped media grant alone leaves the app with
        // a MediaStore view that can be nearly empty (no folders, no shorts) and breaks
        // the direct file operations (favorites move, rename, delete). Steer to the
        // "All files access" toggle instead — the reliable v5.5.2 flow.
        PermissionUtils.isLimitedMediaAccess(context) ->
          PermissionUtils.requestStorageAccess(context) {}

        isGranted -> coroutineScope.launch {
          delay(600)
          navigateToMain()
        }

        // Runtime media permission still missing on an "All files access" build: send the
        // user to the dedicated settings toggle, which is the only way to grant it.
        else -> PermissionUtils.requestStorageAccess(context) {
          // Scoped builds land here directly; nothing more to request.
        }
      }
    }

    fun requestAllPermissions() {
      val needed = PermissionUtils.getRequiredRuntimePermissions()
        .filter {
          androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        }
      if (needed.isNotEmpty()) {
        permissionsLauncher.launch(needed.toTypedArray())
        return
      }
      when {
        PermissionUtils.isLimitedMediaAccess(context) ->
          PermissionUtils.requestStorageAccess(context) {}

        isGranted -> navigateToMain()

        else -> PermissionUtils.requestStorageAccess(context) {}
      }
    }

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            0f to BgHighlight.copy(alpha = 0.85f),
            0.45f to Bg,
            1f to Bg,
          )
        ),
    ) {
      val contentWidth = if (maxWidth > 520.dp) 480.dp else maxWidth
      // Capture before entering nested scopes: inside the scrollable Column the
      // BoxWithConstraints receivers are no longer implicit.
      val screenMaxHeight = maxHeight

      Column(
        modifier = Modifier
          .width(contentWidth)
          .align(Alignment.TopCenter)
          .fillMaxHeight()
          .padding(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            start = 18.dp,
            end = 18.dp,
          )
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Spacer(modifier = Modifier.height(2.dp))

        // ---- Header: logo badge + app name ----
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color.White),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              painter = painterResource(R.drawable.magen_gillan_logo),
              contentDescription = stringResource(R.string.welcome_logo_description),
              modifier = Modifier.size(56.dp),
              contentScale = ContentScale.Fit,
            )
          }
          Column {
            Text(
              text = stringResource(R.string.app_name),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = stringResource(R.string.welcome_by_line),
              fontSize = 12.sp,
              color = TextMuted,
            )
          }
        }

        // ---- Hero image with overlaid title ----
        // The reference design gives the hero the leftover screen height (flex:1).
        // A weight() inside a verticalScroll Column collapses to its min height (the
        // column measures children with unbounded height), which is why the hero used
        // to render as a short banner — so size it from the parent BoxWithConstraints.
        val heroHeight = (screenMaxHeight * 0.52f).coerceIn(300.dp, 680.dp)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, Line, RoundedCornerShape(26.dp)),
        ) {
          Image(
            painter = painterResource(R.drawable.alisha_hero),
            contentDescription = stringResource(R.string.welcome_character_description),
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            // object-position: 50% 16% in the design — keep Alisha's face in frame.
            alignment = BiasAlignment(0f, -0.68f),
          )
          Box(
            modifier = Modifier
              .matchParentSize()
              .background(
                Brush.verticalGradient(
                  0.62f to Color.Transparent,
                  1f to Color(0xC80A1020),
                )
              )
          )
          Text(
            text = stringResource(R.string.welcome_title),
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(18.dp),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
            color = TextPrimary,
          )
        }

        // ---- Lead ----
        Text(
          text = stringResource(R.string.welcome_subtitle),
          fontSize = 13.sp,
          lineHeight = 20.sp,
          color = TextMuted,
        )

        // ---- Mood note ----
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NoteBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(text = "👋", fontSize = 18.sp)
          Text(
            text = stringResource(
              if (isGranted) R.string.welcome_permission_mood_granted
              else R.string.welcome_permission_mood_denied
            ),
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = TextPrimary,
          )
        }

        // ---- Language row ----
        val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable { showLanguageDialog = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(IconCircle),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Language,
              contentDescription = null,
              tint = TextPrimary,
              modifier = Modifier.size(19.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.welcome_language_section_title),
              fontSize = 11.sp,
              color = Accent,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = if (currentLanguage.code.isEmpty()) {
                stringResource(R.string.system_default)
              } else {
                "${currentLanguage.nativeName} (${currentLanguage.localizedName})"
              },
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
            )
          }
          Text(
            text = stringResource(R.string.browse),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Accent,
            modifier = Modifier.clickable { showLanguageDialog = true },
          )
        }

        // ---- Storage permission row ----
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(IconCircle),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Folder,
              contentDescription = null,
              tint = if (isGranted) Accent else TextPrimary,
              modifier = Modifier.size(19.dp),
            )
          }
          Column {
            Text(
              text = stringResource(
                if (isGranted) R.string.storage_access_granted
                else R.string.storage_access_required
              ),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
            )
            Text(
              text = if (isGranted) {
                stringResource(R.string.storage_access_granted_desc)
              } else {
                stringResource(R.string.permission_required_all_files)
              },
              fontSize = 12.sp,
              lineHeight = 18.sp,
              color = TextMuted,
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
      }

      // ---- Sticky actions ----
      Column(
        modifier = Modifier
          .width(contentWidth)
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(GoStart, GoEnd)))
            .clickable {
              if (isGranted) navigateToMain() else requestAllPermissions()
            },
          contentAlignment = Alignment.Center,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 14.dp),
          ) {
            Icon(
              imageVector = if (isGranted) Icons.Filled.Check
              else Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              tint = Bg,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = stringResource(
                if (isGranted) R.string.welcome_get_started
                else R.string.welcome_grant_permission
              ),
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Bg,
            )
          }
        }

        Text(
          text = stringResource(R.string.welcome_skip_permission),
          fontSize = 13.sp,
          color = TextMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { navigateToMain() }
            .padding(vertical = 8.dp),
        )
      }
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
      val currentCode = remember { LocaleHelper.getSavedLanguageCode(context) }
      var selectedCode by remember { mutableStateOf(currentCode) }
      val languages = remember { LocaleHelper.getSupportedLanguages(context) }

      AlertDialog(
        onDismissRequest = { showLanguageDialog = false },
        containerColor = Card,
        titleContentColor = TextPrimary,
        textContentColor = TextMuted,
        icon = {
          Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = Accent,
          )
        },
        title = {
          Text(
            text = stringResource(R.string.pref_appearance_language_title),
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 350.dp)
              .verticalScroll(rememberScrollState())
              .selectableGroup(),
          ) {
            languages.forEach { language ->
              val isSelected = selectedCode == language.code
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .selectable(
                    selected = isSelected,
                    onClick = { selectedCode = language.code },
                    role = Role.RadioButton,
                  )
                  .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = language.nativeName,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = TextPrimary,
                  )
                  if (language.code.isNotEmpty()) {
                    Text(
                      text = language.localizedName,
                      fontSize = 12.sp,
                      color = TextMuted,
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(
            onClick = {
              LocaleHelper.setAppLanguage(context, selectedCode)
              showLanguageDialog = false
            }
          ) {
            Text(
              text = stringResource(R.string.generic_confirm),
              color = Accent,
              fontWeight = FontWeight.SemiBold,
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { showLanguageDialog = false }) {
            Text(
              text = stringResource(R.string.generic_cancel),
              color = TextMuted,
            )
          }
        },
        shape = RoundedCornerShape(24.dp),
      )
    }

    // Auto-advance once permission is granted (e.g. granted from system settings screen)
    LaunchedEffect(isGranted) {
      if (isGranted) {
        delay(900)
        navigateToMain()
      }
    }
  }
}

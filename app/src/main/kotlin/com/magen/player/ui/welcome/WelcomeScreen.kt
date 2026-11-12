package com.magen.player.ui.welcome

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import com.magen.player.BuildConfig
import com.magen.player.R
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.presentation.Screen
import com.magen.player.ui.browser.MainScreen
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.utils.locale.LocaleHelper
import com.magen.player.utils.permission.PermissionUtils

private val WelcomeBackground = Color(0xFF0A1020)
private val WelcomeSurface = Color(0xFF121A2E)
private val WelcomeSurfaceRaised = Color(0xFF1B2745)
private val WelcomeLine = Color(0xFF22304F)
private val WelcomeText = Color(0xFFEEF3FF)
private val WelcomeMuted = Color(0xFF9AAACB)
private val WelcomeBlue = Color(0xFF6EA2FF)

@Serializable
object WelcomeScreen : Screen {

  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExplanationDialog by remember { mutableStateOf(false) }
    var isGranted by remember { mutableStateOf(PermissionUtils.isStoragePermissionGranted(context)) }

    DisposableEffect(Unit) {
      val lifecycleOwner = context as? LifecycleOwner
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          isGranted = PermissionUtils.isStoragePermissionGranted(context)
        }
      }
      lifecycleOwner?.lifecycle?.addObserver(observer)
      onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
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

    val permissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) {
      isGranted = PermissionUtils.isStoragePermissionGranted(context)
      if (isGranted) navigateToMain()
    }

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = WelcomeBackground,
      bottomBar = {
        Surface(color = WelcomeBackground, tonalElevation = 0.dp) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Button(
              onClick = {
                if (isGranted) navigateToMain() else {
                  PermissionUtils.requestStorageAccess(context) {
                    permissionLauncher.launch(PermissionUtils.getStoragePermission())
                  }
                }
              },
              modifier = Modifier.fillMaxWidth().height(52.dp),
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF91B4FF),
                contentColor = WelcomeBackground,
              ),
              contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGranted) {
                  Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(19.dp))
                  Spacer(Modifier.width(8.dp))
                }
                Text(
                  text = stringResource(if (isGranted) R.string.welcome_get_started else R.string.welcome_grant_permission),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            if (!isGranted) {
              TextButton(onClick = { navigateToMain() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                  stringResource(R.string.welcome_skip_permission),
                  color = WelcomeMuted,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }
        }
      },
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color(0xFF142B53), WelcomeBackground),
              startY = 0f,
              endY = 700f,
            )
          )
          .padding(paddingValues)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(R.drawable.welcome_brand_mark),
            contentDescription = stringResource(R.string.welcome_brand_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(56.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .padding(3.dp),
          )
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              stringResource(R.string.app_name),
              color = WelcomeText,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              stringResource(R.string.welcome_byline),
              color = WelcomeMuted,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 205.dp, max = 310.dp)
            .clip(RoundedCornerShape(25.dp))
            .border(1.dp, WelcomeLine, RoundedCornerShape(25.dp)),
        ) {
          Image(
            painter = painterResource(R.drawable.welcome_hero),
            contentDescription = stringResource(R.string.welcome_hero_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier = Modifier.fillMaxSize().background(
              Brush.verticalGradient(
                0.48f to Color.Transparent,
                1f to Color(0xD90A1020),
              )
            ),
          )
          Text(
            stringResource(R.string.welcome_hero_title),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 14.dp),
            color = WelcomeText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
        }

        Text(
          stringResource(R.string.welcome_offline_description),
          modifier = Modifier.padding(vertical = 1.dp),
          color = WelcomeMuted,
          style = MaterialTheme.typography.bodyMedium,
          lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
        )

        if (!isGranted) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(WelcomeSurfaceRaised)
              .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = WelcomeBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              stringResource(R.string.welcome_permission_note),
              color = WelcomeText,
              style = MaterialTheme.typography.bodySmall,
              lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
            )
          }
        }

        LanguageCard(onClick = { showLanguageDialog = true })
        StorageCard(
          isGranted = isGranted,
          onExplain = { showExplanationDialog = true },
        )
      }
    }

    if (showLanguageDialog) {
      val currentCode = remember { LocaleHelper.getSavedLanguageCode(context) }
      var selectedCode by remember { mutableStateOf(currentCode) }
      val languages = remember { LocaleHelper.getSupportedLanguages(context) }

      AlertDialog(
        onDismissRequest = { showLanguageDialog = false },
        icon = { Icon(Icons.Filled.Language, contentDescription = null, tint = WelcomeBlue) },
        title = { Text(stringResource(R.string.pref_appearance_language_title), fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
          ) {
            languages.forEach { language ->
              val selected = selectedCode == language.code
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable(role = Role.RadioButton) { selectedCode = language.code }
                  .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = selected, onClick = { selectedCode = language.code })
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(language.nativeName, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                  if (language.code.isNotEmpty()) {
                    Text(language.localizedName, style = MaterialTheme.typography.bodySmall)
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          FilledTonalButton(
            onClick = {
              LocaleHelper.setAppLanguage(context, selectedCode)
              showLanguageDialog = false
            },
          ) { Text(stringResource(R.string.generic_confirm)) }
        },
        dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.generic_cancel)) } },
        shape = RoundedCornerShape(24.dp),
      )
    }

    if (showExplanationDialog) {
      val isPlayStoreBuild = BuildConfig.SCOPED_STORAGE_ONLY
      AlertDialog(
        onDismissRequest = { showExplanationDialog = false },
        icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = WelcomeBlue) },
        title = {
          Text(
            stringResource(R.string.why_this_permission_is_needed),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Column(
            modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (isPlayStoreBuild) {
              Text(stringResource(R.string.permission_explanation_playstore_intro))
              Text(
                stringResource(
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    R.string.permission_explanation_tiramisu_plus
                  else R.string.permission_explanation_pre_tiramisu
                )
              )
              Text(stringResource(R.string.permission_used_exclusively_for), fontWeight = FontWeight.Medium)
              Text(stringResource(R.string.permission_usage_bullet_list))
            } else {
              Text(stringResource(R.string.permission_explanation_standard_intro))
              Text(stringResource(R.string.permission_security_policy_change))
              Text(stringResource(R.string.permission_privacy_assurance_standard))
            }
            Text(stringResource(R.string.privacy_assurance_final), fontWeight = FontWeight.Medium)
          }
        },
        confirmButton = {
          FilledTonalButton(onClick = { showExplanationDialog = false }) {
            Text(stringResource(R.string.got_it))
          }
        },
        shape = RoundedCornerShape(20.dp),
      )
    }
  }
}

@Composable
private fun LanguageCard(onClick: () -> Unit) {
  val context = LocalContext.current
  val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = WelcomeSurface),
    border = androidx.compose.foundation.BorderStroke(1.dp, WelcomeLine),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircleIcon(icon = { Icon(Icons.Filled.Language, contentDescription = null, tint = WelcomeText) })
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.welcome_language_label), color = WelcomeBlue, style = MaterialTheme.typography.labelMedium)
        Text(
          if (currentLanguage.code.isEmpty()) stringResource(R.string.system_default)
          else "${currentLanguage.nativeName} (${currentLanguage.localizedName})",
          color = WelcomeText,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(onClick = onClick) { Text(stringResource(R.string.browse), color = WelcomeBlue, fontWeight = FontWeight.SemiBold) }
    }
  }
}

@Composable
private fun StorageCard(isGranted: Boolean, onExplain: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = WelcomeSurface),
    border = androidx.compose.foundation.BorderStroke(1.dp, WelcomeLine),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      CircleIcon(
        icon = {
          Icon(
            if (isGranted) Icons.Filled.Check else Icons.Filled.Folder,
            contentDescription = null,
            tint = WelcomeText,
          )
        }
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          stringResource(if (isGranted) R.string.storage_access_granted else R.string.storage_access_required),
          color = WelcomeText,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          stringResource(
            when {
              isGranted -> R.string.storage_access_granted_desc
              BuildConfig.SCOPED_STORAGE_ONLY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> R.string.permission_required_photos_videos
              BuildConfig.SCOPED_STORAGE_ONLY -> R.string.permission_required_storage
              else -> R.string.permission_required_all_files
            }
          ),
          color = WelcomeMuted,
          style = MaterialTheme.typography.bodySmall,
          lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
        )
        if (!isGranted) {
          TextButton(onClick = onExplain, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(17.dp), tint = WelcomeBlue)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.why_do_i_see_this), color = WelcomeBlue)
          }
        }
      }
    }
  }
}

@Composable
private fun CircleIcon(icon: @Composable () -> Unit) {
  Surface(
    modifier = Modifier.size(38.dp),
    shape = CircleShape,
    color = Color(0xFF1F2C4B),
  ) {
    Box(contentAlignment = Alignment.Center) { icon() }
  }
}

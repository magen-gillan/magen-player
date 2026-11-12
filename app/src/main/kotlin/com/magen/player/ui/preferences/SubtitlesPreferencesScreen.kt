package com.magen.player.ui.preferences

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.documentfile.provider.DocumentFile
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import com.magen.player.utils.media.OpenDocumentTreeContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.magen.player.R
import com.magen.player.preferences.SubtitlesPreferences
import com.magen.player.preferences.preference.collectAsState
import com.magen.player.presentation.Screen
import com.magen.player.presentation.components.GroupPosition
import com.magen.player.presentation.components.GroupedListColumn
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.ui.utils.navigateBack
import com.magen.player.utils.media.CustomFontEntry
import com.magen.player.utils.media.copyFontsFromDirectory
import com.magen.player.utils.media.loadCustomFontEntries
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import com.magen.player.ui.preferences.components.SwitchPreference
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.net.Uri
import com.magen.player.utils.media.SubtitleEncodingUtils
import org.koin.compose.koinInject

@Serializable
object SubtitlesPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<SubtitlesPreferences>()
    val fileManager = koinInject<FileManager>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_subtitles),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(
              onClick = backstack::navigateBack,
            ) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val fontsFolder by preferences.fontsFolder.collectAsState()
        var availableFonts by remember { mutableStateOf<List<String>>(emptyList()) }
        var customFontEntries by remember { mutableStateOf<List<CustomFontEntry>>(emptyList()) }
        var fontLoadTrigger by remember { mutableIntStateOf(0) }
        var isLoadingFonts by remember { mutableStateOf(false) }

        val locationPicker =
          rememberLauncherForActivityResult(
            OpenDocumentTreeContract(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.fontsFolder.set(uri.toString())

            // Copy fonts immediately in background
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
              isLoadingFonts = true
              copyFontsFromDirectory(context, fileManager, uri.toString())
              withContext(Dispatchers.Main) {
                fontLoadTrigger++
                isLoadingFonts = false
              }
            }
          }

        // Load fonts when folder changes or trigger is fired
        LaunchedEffect(fontsFolder, fontLoadTrigger) {
          customFontEntries = loadCustomFontEntries(context)
          availableFonts = listOf("Default") + customFontEntries.map { it.familyName }
        }

        // Auto-refresh fonts on app restart if directory is set
        LaunchedEffect(Unit) {
          if (fontsFolder.isNotBlank()) {
            isLoadingFonts = true
            withContext(Dispatchers.IO) {
              copyFontsFromDirectory(context, fileManager, fontsFolder)
            }
            fontLoadTrigger++
            isLoadingFonts = false
          }
        }

        val subtitleSaveFolder by preferences.subtitleSaveFolder.collectAsState()

        val saveLocationPicker =
          rememberLauncherForActivityResult(
            OpenDocumentTreeContract(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.subtitleSaveFolder.set(uri.toString())
          }

        val navBarHeight = com.magen.player.ui.browser.LocalNavigationBarHeight.current
        LazyColumn(
          state = rememberPreferenceLazyListState(),
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          // === GENERAL SECTION ===
          item {
            PreferenceSectionHeader(title = stringResource(R.string.general))
          }

          item {
            val preferredLanguages by preferences.preferredLanguages.collectAsState()
            val disableByDefault by preferences.disableSubtitlesByDefault.collectAsState()
            val autoload by preferences.autoloadMatchingSubtitles.collectAsState()
            val overrideAss by preferences.overrideAssSubs.collectAsState()
            val scaleByWindow by preferences.scaleByWindow.collectAsState()
            val forceLtr by preferences.forceLtr.collectAsState()
            val openAtVideoLocation by preferences.openPickerAtVideoLocation.collectAsState()
            val customSubtitleFolder by preferences.customSubtitleFolder.collectAsState()
            val customFolderPicker =
              rememberLauncherForActivityResult(
                OpenDocumentTreeContract(),
              ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult

                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                
                val path = getSimplifiedPathFromUri(uri.toString())
                preferences.customSubtitleFolder.set(path)
              }

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_subtitles, R.string.pref_preferred_languages),
              ) {
                TextFieldPreference(
                  value = preferredLanguages,
                  onValueChange = preferences.preferredLanguages::set,
                  textToValue = { it },
                  title = { Text(stringResource(R.string.pref_preferred_languages)) },
                  summary = {
                    if (preferredLanguages.isNotBlank()) {
                      Text(
                        preferredLanguages,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    } else {
                      Text(
                        stringResource(R.string.not_set_video_default),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  },
                  textField = { value, onValueChange, _ ->
                    Column {
                      Text(stringResource(R.string.enter_language_codes))
                      TextField(
                        value,
                        onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.language_codes_placeholder)) },
                      )
                    }
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_disable_by_default_title,
              ) {
                SwitchPreference(
                  value = disableByDefault,
                  onValueChange = { preferences.disableSubtitlesByDefault.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_disable_by_default_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_disable_by_default_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_autoload_title,
              ) {
                SwitchPreference(
                  value = autoload,
                  onValueChange = { preferences.autoloadMatchingSubtitles.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_autoload_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_autoload_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.player_sheets_sub_override_ass,
              ) {
                SwitchPreference(
                  value = overrideAss,
                  onValueChange = { preferences.overrideAssSubs.set(it) },
                  title = { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
                  summary = {
                    Text(
                      stringResource(R.string.player_sheets_sub_override_ass_subtitle),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.player_sheets_sub_scale_by_window,
              ) {
                SwitchPreference(
                  value = scaleByWindow,
                  onValueChange = { preferences.scaleByWindow.set(it) },
                  title = { Text(stringResource(R.string.player_sheets_sub_scale_by_window)) },
                  summary = {
                    Text(
                      stringResource(R.string.player_sheets_sub_scale_by_window_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_force_ltr_title,
              ) {
                SwitchPreference(
                  value = forceLtr,
                  onValueChange = { preferences.forceLtr.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_force_ltr_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_force_ltr_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              val subtitleEncoding by preferences.subtitleEncoding.collectAsState()
              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_encoding_title,
              ) {
                ListPreference(
                  value = subtitleEncoding,
                  onValueChange = { preferences.subtitleEncoding.set(it) },
                  values = SubtitleEncodingUtils.ENCODINGS.map { it.code },
                  valueToText = { androidx.compose.ui.text.AnnotatedString(SubtitleEncodingUtils.getDisplayName(it)) },
                  title = { Text(stringResource(R.string.pref_subtitles_encoding_title)) },
                  summary = {
                    Text(
                      SubtitleEncodingUtils.getDisplayName(subtitleEncoding),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_open_at_video_location_title,
              ) {
                SwitchPreference(
                  value = openAtVideoLocation,
                  onValueChange = { preferences.openPickerAtVideoLocation.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_open_at_video_location_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_open_at_video_location_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_custom_picker_folder_title,
              ) {
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clickable { customFolderPicker.launch(null) }
                      .padding(vertical = 16.dp, horizontal = 16.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        stringResource(R.string.pref_subtitles_custom_picker_folder_title),
                        style = MaterialTheme.typography.titleMedium,
                      )
                      Text(
                        if (customSubtitleFolder.isBlank()) stringResource(R.string.not_set_video_default) else customSubtitleFolder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (customSubtitleFolder.isNotBlank()) {
                      IconButton(onClick = { preferences.customSubtitleFolder.set("") }) {
                        Icon(
                          Icons.Default.Clear,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.tertiary,
                        )
                      }
                    }
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_subtitles_fonts_dir,
              ) {
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clickable { locationPicker.launch(null) }
                      .padding(vertical = 16.dp, horizontal = 16.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                    ) {
                      Text(
                        stringResource(R.string.pref_subtitles_fonts_dir),
                        style = MaterialTheme.typography.titleMedium,
                      )
                      if (fontsFolder.isBlank()) {
                        Text(
                          stringResource(R.string.not_set_system_fonts),
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      } else {
                        if (availableFonts.isNotEmpty()) {
                          Text(
                            stringResource(R.string.fonts_loaded, availableFonts.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                          )
                        }
                      }
                    }

                    if (fontsFolder.isNotBlank()) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoadingFonts) {
                          Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                          ) {
                            CircularProgressIndicator(
                              modifier = Modifier.size(24.dp),
                              strokeWidth = 2.dp,
                              color = MaterialTheme.colorScheme.primary,
                            )
                          }
                        } else {
                          IconButton(
                            onClick = {
                              kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                isLoadingFonts = true
                                copyFontsFromDirectory(context, fileManager, fontsFolder)
                                withContext(Dispatchers.Main) {
                                  fontLoadTrigger++
                                  isLoadingFonts = false
                                }
                              }
                            },
                          ) {
                            Icon(
                              Icons.Default.Refresh,
                              contentDescription = stringResource(R.string.reload_fonts),
                              tint = MaterialTheme.colorScheme.primary,
                            )
                          }
                        }

                        IconButton(
                          onClick = {
                            preferences.fontsFolder.set("")
                            fontLoadTrigger++
                          },
                        ) {
                          Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear_font_directory),
                            tint = MaterialTheme.colorScheme.tertiary,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_subtitles_downloads_title))
          }

          item {
            var showClearDialog by remember { mutableStateOf(false) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_subtitles_save_location,
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveLocationPicker.launch(null) }
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                ) {
                  Column {
                    Text(
                      stringResource(R.string.pref_subtitles_save_location),
                      style = MaterialTheme.typography.titleMedium,
                    )
                    val folderPath = if (subtitleSaveFolder.isBlank()) {
                      stringResource(R.string.not_set_video_default)
                    } else {
                      Uri.parse(subtitleSaveFolder).path ?: subtitleSaveFolder
                    }
                    Text(
                      text = folderPath,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_subtitles_clear_downloads,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_subtitles_clear_downloads), color = MaterialTheme.colorScheme.error) },
                  summary = { Text(stringResource(R.string.pref_subtitles_clear_downloads_summary)) },
                  onClick = { showClearDialog = true },
                  enabled = subtitleSaveFolder.isNotBlank(),
                )

                if (showClearDialog) {
                  AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text(stringResource(R.string.pref_subtitles_clear_downloads)) },
                    text = { Text(stringResource(R.string.pref_subtitles_clear_downloads_confirmation)) },
                    confirmButton = {
                      TextButton(
                        onClick = {
                          showClearDialog = false
                          scope.launch(Dispatchers.IO) {
                            runCatching {
                              DocumentFile.fromTreeUri(context, Uri.parse(subtitleSaveFolder))
                                ?.listFiles()?.forEach { it.delete() }
                              withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, R.string.toast_subtitles_cleared, android.widget.Toast.LENGTH_SHORT).show()
                              }
                            }.onFailure { error ->
                              withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Error: ${error.message}", android.widget.Toast.LENGTH_SHORT).show()
                              }
                            }
                          }
                        },
                      ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                      TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                      }
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

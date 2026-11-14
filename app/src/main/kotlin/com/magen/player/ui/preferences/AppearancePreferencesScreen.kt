package com.magen.player.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.magen.player.R
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.preferences.MultiChoiceSegmentedButton
import com.magen.player.ui.preferences.components.ThemePicker
import com.magen.player.ui.preferences.components.SwitchPreference
import com.magen.player.preferences.preference.collectAsState
import com.magen.player.presentation.Screen
import com.magen.player.presentation.components.GroupPosition
import com.magen.player.presentation.components.GroupedListColumn
import com.magen.player.ui.theme.DarkMode
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.ui.utils.navigateBack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.semantics.Role
import com.magen.player.utils.locale.LocaleHelper
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@Serializable
object AppearancePreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val preferences = koinInject<AppearancePreferences>()
        val browserPreferences = koinInject<BrowserPreferences>()
        val backstack = LocalBackStack.current
        val systemDarkTheme = isSystemInDarkTheme()

        val darkMode by preferences.darkMode.collectAsState()
        val appTheme by preferences.appTheme.collectAsState()

        // Determine if we're in dark mode for theme preview
        val isDarkMode = when (darkMode) {
            DarkMode.Dark -> true
            DarkMode.Light -> false
            DarkMode.System -> systemDarkTheme
        }

        val context = LocalContext.current
        var showLanguageDialog by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.pref_appearance_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::navigateBack) {
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
            val navBarHeight = com.magen.player.ui.browser.LocalNavigationBarHeight.current
            ProvidePreferenceLocals {
                LazyColumn(
                    state = rememberPreferenceLazyListState(),
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
                ) {
                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_language_title))
                    }

                    item {
                        val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.ONLY,
                                highlightKey = R.string.pref_appearance_language_title,
                            ) {
                                Preference(
                                    title = { Text(text = stringResource(id = R.string.pref_appearance_language_title)) },
                                    summary = {
                                        Text(
                                            text = if (currentLanguage.code.isEmpty()) {
                                                stringResource(R.string.system_default)
                                            } else {
                                                "${currentLanguage.nativeName} (${currentLanguage.localizedName})"
                                            },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = { showLanguageDialog = true },
                                )
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_theme))
                    }

                    item {
                        val amoledMode by preferences.amoledMode.collectAsState()
                        val useSystemFont by preferences.useSystemFont.collectAsState()
                        val hidePlayFabShowInHeader by preferences.hidePlayFabShowInHeader.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = listOf(
                                    R.string.pref_appearance_title,
                                    R.string.pref_appearance_amoled_mode_title,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    MultiChoiceSegmentedButton(
                                        choices = DarkMode.entries.map { stringResource(it.titleRes) }.toImmutableList(),
                                        selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
                                        onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
                                    )
                                    ThemePicker(
                                        currentTheme = appTheme,
                                        isDarkMode = isDarkMode,
                                        onThemeSelected = { preferences.appTheme.set(it) },
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                    SwitchPreference(
                                        value = amoledMode,
                                        onValueChange = { preferences.amoledMode.set(it) },
                                        title = { Text(text = stringResource(id = R.string.pref_appearance_amoled_mode_title)) },
                                        summary = {
                                            Text(
                                                text = stringResource(id = R.string.pref_appearance_amoled_mode_summary),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        },
                                        enabled = darkMode != DarkMode.Light,
                                    )
                                }
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_use_system_font_title,
                            ) {
                                SwitchPreference(
                                    value = useSystemFont,
                                    onValueChange = { preferences.useSystemFont.set(it) },
                                    title = { Text(text = stringResource(id = R.string.pref_appearance_use_system_font_title)) },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_use_system_font_summary),
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_appearance_hide_play_fab_show_in_header_title,
                            ) {
                                SwitchPreference(
                                    value = hidePlayFabShowInHeader,
                                    onValueChange = { preferences.hidePlayFabShowInHeader.set(it) },
                                    title = { Text(text = stringResource(id = R.string.pref_appearance_hide_play_fab_show_in_header_title)) },
                                    summary = { Text(text = stringResource(id = R.string.pref_appearance_hide_play_fab_show_in_header_summary)) },
                                )
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_bottom_nav))
                    }

                    item {
                        // Fixed tab set: home and favorites are always on; only
                        // the Shorts tab can be switched off.
                        val enableShorts by browserPreferences.enableShorts.collectAsState()

                        data class FixedTabConfig(val titleRes: Int, val summaryRes: Int, val isShorts: Boolean)

                        val tabConfigs = listOf(
                            FixedTabConfig(R.string.pref_appearance_tab_home_title, R.string.pref_appearance_tab_home_summary, false),
                            FixedTabConfig(R.string.pref_appearance_tab_shorts_title, R.string.pref_appearance_tab_shorts_summary, true),
                            FixedTabConfig(R.string.pref_appearance_tab_favorites_title, R.string.pref_appearance_tab_favorites_summary, false),
                        )

                        GroupedListColumn {
                            tabConfigs.forEachIndexed { index, config ->
                                GroupedPreferenceCard(
                                    position = when {
                                        tabConfigs.size == 1 -> GroupPosition.ONLY
                                        index == 0 -> GroupPosition.FIRST
                                        index == tabConfigs.lastIndex -> GroupPosition.LAST
                                        else -> GroupPosition.MIDDLE
                                    },
                                    highlightKey = config.titleRes,
                                ) {
                                    SwitchPreference(
                                        value = if (config.isShorts) enableShorts else true,
                                        onValueChange = { if (config.isShorts) browserPreferences.enableShorts.set(it) },
                                        enabled = config.isShorts,
                                        title = { Text(text = stringResource(id = config.titleRes)) },
                                        summary = {
                                            Text(
                                                text = stringResource(id = config.summaryRes),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }


                }
            }
        }

        if (showLanguageDialog) {
            val currentCode = remember { LocaleHelper.getSavedLanguageCode(context) }
            var selectedCode by remember { mutableStateOf(currentCode) }
            val languages = remember { LocaleHelper.getSupportedLanguages(context) }

            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.pref_appearance_language_title),
                        style = MaterialTheme.typography.headlineSmall,
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
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (language.code.isNotEmpty()) {
                                        Text(
                                            text = language.localizedName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
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
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.generic_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.generic_cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp),
            )
        }
    }
}

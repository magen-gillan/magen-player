package com.magen.player.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magen.player.R
import com.magen.player.database.dao.ShortsMediaDao
import com.magen.player.presentation.Screen
import com.magen.player.ui.browser.LocalNavigationBarHeight
import com.magen.player.ui.browser.shorts.ShortsScreen
import com.magen.player.ui.utils.LocalBackStack
import com.magen.player.ui.utils.navigateBack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object FavoriteShortsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable override fun Content() {
        val backstack = LocalBackStack.current
        val dao = koinInject<ShortsMediaDao>()
        val all by dao.observeAllShortsMedia().collectAsState(initial = emptyList())
        val favorites = all.filter { it.isLoved && File(it.path).exists() }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorite_shorts), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = backstack::navigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } }
            )
        }) { padding ->
            if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.favorite_shorts_empty))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LocalNavigationBarHeight.current + 16.dp)) {
                    items(favorites, key = { it.path }) { item ->
                        ListItem(
                            headlineContent = { Text(File(item.path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(item.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { backstack.add(ShortsScreen(initialVideoPath = item.path, favoritesOnly = true)) }
                        )
                    }
                }
            }
        }
    }
}

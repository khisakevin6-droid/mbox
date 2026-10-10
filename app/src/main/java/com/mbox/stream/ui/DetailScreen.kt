package com.mbox.stream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbox.stream.AppViewModel
import com.mbox.stream.CatalogState
import com.mbox.stream.domain.Episode
import com.mbox.stream.domain.StreamSource
import com.mbox.stream.domain.Title
import com.mbox.stream.domain.TitleType
import com.mbox.stream.player.PlayerLauncher
import com.mbox.stream.sources.ProviderResult
import com.mbox.stream.sources.SourceResult

private data class PickTarget(val episode: Episode?)

@Composable
fun DetailScreen(vm: AppViewModel, id: String, autoplay: Boolean, onBack: () -> Unit) {
    val state by vm.catalog.collectAsState()
    val title = (state as? CatalogState.Ready)?.titles?.firstOrNull { it.id == id }
    if (title == null) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            EmptyView("This title is not available in the current catalogue.")
        }
    } else {
        DetailContent(vm, title, autoplay, onBack)
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, title: Title, autoplay: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var pick by remember { mutableStateOf<PickTarget?>(null) }
    var season by remember { mutableStateOf(title.seasons.firstOrNull()?.number ?: 1) }

    LaunchedEffect(title.id) {
        if (autoplay && title.type == TitleType.MOVIE) pick = PickTarget(null)
    }

    val isFavorite = entries.any { it.titleId == title.id && it.kind == "favorite" }
    val inWatchlist = entries.any { it.titleId == title.id && it.kind == "watchlist" }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                Artwork(title, Modifier.fillMaxWidth().height(230.dp), url = title.backdropUrl ?: title.posterUrl)
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val meta = listOfNotNull(
                    title.year.takeIf { it > 0 }?.toString(),
                    title.runtimeMinutes.takeIf { it > 0 }?.let { "${it} min" },
                    if (title.rating > 0) "★ ${"%.1f".format(title.rating)}" else null,
                    if (title.type == TitleType.SERIES) "Series" else "Movie"
                ).joinToString("  •  ")
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (title.genres.isNotEmpty()) {
                    Text(title.genres.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (title.demo) {
                    Text(
                        "Demonstration entry: metadata is illustrative and sources are samples, not verified for your use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title.type == TitleType.MOVIE) {
                        Button(onClick = { pick = PickTarget(null) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Play")
                        }
                    }
                    OutlinedButton(onClick = { vm.toggleList(title.id, "favorite") }) {
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                        Text(if (isFavorite) "Favorited" else "Favorite")
                    }
                    OutlinedButton(onClick = { vm.toggleList(title.id, "watchlist") }) {
                        Icon(if (inWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null)
                        Text(if (inWatchlist) "Listed" else "Watchlist")
                    }
                }
                if (title.synopsis.isNotBlank()) Text(title.synopsis)
            }
        }
        if (title.type == TitleType.SERIES) {
            if (title.seasons.isEmpty()) {
                item { Text("No episodes listed.", Modifier.padding(16.dp)) }
            } else {
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(title.seasons) { s ->
                            FilterChip(
                                selected = s.number == season,
                                onClick = { season = s.number },
                                label = { Text("Season ${s.number}") }
                            )
                        }
                    }
                }
                val episodes = title.seasons.firstOrNull { it.number == season }?.episodes ?: emptyList()
                items(episodes, key = { it.key }) { ep ->
                    ListItem(
                        modifier = Modifier.clickable { pick = PickTarget(ep) },
                        headlineContent = { Text("E${ep.number} · ${ep.title}") },
                        supportingContent = {
                            if (ep.runtimeMinutes > 0) Text("${ep.runtimeMinutes} min")
                        },
                        trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
                    )
                }
            }
        }
    }

    pick?.let { target ->
        SourcePickerDialog(
            vm = vm,
            title = title,
            episode = target.episode,
            onDismiss = { pick = null },
            onPlay = { source ->
                pick = null
                val label = target.episode?.let { "${title.title} – S${it.season}E${it.number}" } ?: title.title
                PlayerLauncher.start(
                    context,
                    label = label,
                    titleId = title.id,
                    episodeKey = target.episode?.key ?: "movie",
                    source = source,
                    speed = prefs.speed,
                    subLang = prefs.subLang
                )
            }
        )
    }
}

@Composable
private fun SourcePickerDialog(
    vm: AppViewModel,
    title: Title,
    episode: Episode?,
    onDismiss: () -> Unit,
    onPlay: (StreamSource) -> Unit
) {
    var results by remember { mutableStateOf<List<ProviderResult>?>(null) }
    LaunchedEffect(title.id, episode?.key) { results = vm.loadSources(title, episode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a source") },
        text = {
            val list = results
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { r ->
                        Text(r.providerName, fontWeight = FontWeight.Bold)
                        when (val res = r.result) {
                            is SourceResult.Unavailable -> Text(res.reason, style = MaterialTheme.typography.bodySmall)
                            is SourceResult.Available -> res.sources.forEach { s ->
                                OutlinedButton(onClick = { onPlay(s) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(s.providerName)
                                        Text(
                                            "${s.kind.label} • " + if (s.verified) "Authorized source" else "Demo source",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

package com.mbox.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mbox.stream.AppViewModel
import com.mbox.stream.CatalogState
import com.mbox.stream.domain.Title
import com.mbox.stream.domain.TitleType

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    onGenre: (String) -> Unit
) {
    val state by vm.catalog.collectAsState()
    val progress by vm.progress.collectAsState()
    val entries by vm.entries.collectAsState()

    when (val s = state) {
        is CatalogState.Loading -> LoadingView()
        is CatalogState.Error -> ErrorView(s.message, onRetry = vm::reload)
        is CatalogState.Ready -> {
            if (s.titles.isEmpty()) {
                EmptyView("The catalogue is empty. Add titles to assets/catalog.json or choose another provider in Settings.")
            } else {
                val byId = s.titles.associateBy { it.id }
                val continueItems = progress
                    .filter { it.durationMs > 0 && it.positionMs > 5_000 && it.positionMs < it.durationMs * 0.95 }
                    .mapNotNull { p -> byId[p.titleId]?.let { it to p.positionMs.toFloat() / p.durationMs } }
                    .distinctBy { it.first.id }
                val favoriteIds = entries.filter { it.kind == "favorite" }.map { it.titleId }.toSet()
                val favoriteGenres = favoriteIds.mapNotNull { byId[it] }.flatMap { it.genres }.toSet()
                val recommended = s.titles
                    .filter { it.id !in favoriteIds && it.genres.any { g -> g in favoriteGenres } }
                    .ifEmpty { s.titles.sortedByDescending { it.rating } }
                    .take(12)
                val featured = s.titles.filter { it.hasSources() }.maxByOrNull { it.popularity } ?: s.titles.first()
                val genres = s.titles.flatMap { it.genres }.distinct().sorted()

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { FeaturedBanner(featured, onOpen = { onOpen(featured.id) }, onPlay = { onPlay(featured.id) }) }
                    if (continueItems.isNotEmpty()) {
                        item {
                            TitleRow(
                                "Continue Watching",
                                continueItems.map { it.first },
                                onOpen,
                                continueItems.associate { it.first.id to it.second }
                            )
                        }
                    }
                    item {
                        TitleRow(
                            "Trending Movies",
                            s.titles.filter { it.type == TitleType.MOVIE }.sortedByDescending { it.popularity },
                            onOpen
                        )
                    }
                    val series = s.titles.filter { it.type == TitleType.SERIES }.sortedByDescending { it.popularity }
                    if (series.isNotEmpty()) item { TitleRow("Popular TV Series", series, onOpen) }
                    item { TitleRow("Recently Added", s.titles.sortedByDescending { it.added }.take(12), onOpen) }
                    item { TitleRow("Recommended for You", recommended, onOpen) }
                    if (genres.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle("Genres")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(genres) { g ->
                                        AssistChip(onClick = { onGenre(g) }, label = { Text(g) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedBanner(title: Title, onOpen: () -> Unit, onPlay: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        Artwork(title, Modifier.fillMaxWidth().height(300.dp), url = title.backdropUrl ?: title.posterUrl)
        Box(
            Modifier.fillMaxWidth().height(300.dp).background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            if (title.demo) DemoBadge()
            Text(title.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                title.synopsis,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Play")
                }
                OutlinedButton(onClick = onOpen) { Text("Details") }
            }
        }
    }
}

@Composable
fun TitleRow(
    label: String,
    titles: List<Title>,
    onOpen: (String) -> Unit,
    progress: Map<String, Float> = emptyMap()
) {
    if (titles.isEmpty()) return
    Column {
        SectionTitle(label)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(titles, key = { it.id }) { t ->
                PosterCard(t, onClick = { onOpen(t.id) }, progress = progress[t.id])
            }
        }
    }
}

package com.mbox.stream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbox.stream.AppViewModel
import com.mbox.stream.CatalogState

private enum class SortOption(val label: String) {
    POPULARITY("Popularity"), TITLE("Title (A-Z)"), YEAR("Year (newest)")
}

@Composable
fun DiscoverScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val state by vm.catalog.collectAsState()
    val genre by vm.selectedGenre.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOption.POPULARITY) }
    var menuOpen by remember { mutableStateOf(false) }

    when (val s = state) {
        is CatalogState.Loading -> LoadingView()
        is CatalogState.Error -> ErrorView(s.message, onRetry = vm::reload)
        is CatalogState.Ready -> {
            val genres = s.titles.flatMap { it.genres }.distinct().sorted()
            val activeGenre: String? = genre
            val filtered = s.titles
                .filter { t ->
                    (query.isBlank() || t.title.contains(query.trim(), ignoreCase = true)) &&
                        (activeGenre == null || t.genres.contains(activeGenre))
                }
                .let { list ->
                    when (sort) {
                        SortOption.TITLE -> list.sortedBy { it.title.lowercase() }
                        SortOption.YEAR -> list.sortedByDescending { it.year }
                        SortOption.POPULARITY -> list.sortedByDescending { it.popularity }
                    }
                }

            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search movies & series") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            SortOption.values().forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(o.label + if (o == sort) "  ✓" else "") },
                                    onClick = { sort = o; menuOpen = false }
                                )
                            }
                        }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genres) { g ->
                        FilterChip(
                            selected = g == genre,
                            onClick = { vm.setGenre(if (g == genre) null else g) },
                            label = { Text(g) }
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    EmptyView("No titles match your search.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filtered, key = { it.id }) { t ->
                            PosterCard(t, onClick = { onOpen(t.id) })
                        }
                    }
                }
            }
        }
    }
}

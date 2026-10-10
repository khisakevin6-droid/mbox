package com.mbox.stream.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mbox.stream.AppViewModel
import com.mbox.stream.CatalogState
import com.mbox.stream.data.db.CustomSource
import com.mbox.stream.domain.MediaKind
import com.mbox.stream.domain.StreamSource
import com.mbox.stream.player.PlayerLauncher
import com.mbox.stream.player.formatTime

private val tabNames = listOf("Favorites", "Watchlist", "History", "My Videos")

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (String) -> Unit, onMagnet: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabNames.forEachIndexed { i, n ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(n) })
            }
        }
        when (tab) {
            0 -> TitleGrid(vm, "favorite", "Nothing here yet. Tap Favorite on a title's page.", onOpen)
            1 -> TitleGrid(vm, "watchlist", "Your watchlist is empty. Tap Watchlist on a title's page.", onOpen)
            2 -> HistoryList(vm, onOpen)
            else -> MyVideos(vm, onMagnet)
        }
    }
}

@Composable
private fun TitleGrid(vm: AppViewModel, kind: String, emptyText: String, onOpen: (String) -> Unit) {
    val state by vm.catalog.collectAsState()
    val entries by vm.entries.collectAsState()
    val titles = (state as? CatalogState.Ready)?.titles ?: emptyList()
    val byId = titles.associateBy { it.id }
    val shown = entries.filter { it.kind == kind }.mapNotNull { byId[it.titleId] }
    if (shown.isEmpty()) {
        EmptyView(emptyText)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(shown, key = { it.id }) { t -> PosterCard(t, onClick = { onOpen(t.id) }) }
        }
    }
}

@Composable
private fun HistoryList(vm: AppViewModel, onOpen: (String) -> Unit) {
    val progress by vm.progress.collectAsState()
    val state by vm.catalog.collectAsState()
    val known = ((state as? CatalogState.Ready)?.titles ?: emptyList()).map { it.id }.toSet()
    if (progress.isEmpty()) {
        EmptyView("Nothing watched yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(progress, key = { it.entryKey }) { p ->
            val canOpen = p.titleId in known
            ListItem(
                modifier = if (canOpen) Modifier.clickable { onOpen(p.titleId) } else Modifier,
                headlineContent = { Text(p.label) },
                supportingContent = {
                    Column {
                        Text("${formatTime(p.positionMs)} of ${formatTime(p.durationMs)}")
                        if (p.durationMs > 0) {
                            LinearProgressIndicator(
                                progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MyVideos(vm: AppViewModel, onMagnet: () -> Unit) {
    val context = LocalContext.current
    val sources by vm.customSources.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var showUrlDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Some providers do not offer persistable access; playback still works this session.
            }
            vm.addCustomSource(displayName(context, uri), uri.toString(), true)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Play videos you are authorized to access: a direct https URL (your own server or storage) or a file from this device.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add URL")
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Text("Add file")
            }
        }
        if (prefs.magnetEnabled) {
            OutlinedButton(onClick = onMagnet) { Text("Open magnet tools") }
        }
        message?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        if (sources.isEmpty()) {
            Text("No saved videos yet.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sources, key = { it.id }) { src ->
                    ListItem(
                        modifier = Modifier.clickable {
                            PlayerLauncher.start(
                                context,
                                label = src.name,
                                titleId = "custom",
                                episodeKey = "c${src.id}",
                                source = StreamSource("My Videos", src.uri, MediaKind.AUTO, verified = true),
                                speed = prefs.speed,
                                subLang = prefs.subLang
                            )
                        },
                        headlineContent = { Text(src.name) },
                        supportingContent = { Text(if (src.isLocal) "Local file" else src.uri) },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { vm.deleteCustomSource(src) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        AddUrlDialog(
            onDismiss = { showUrlDialog = false },
            onAdd = { name, url ->
                if (url.startsWith("https://")) {
                    vm.addCustomSource(name.ifBlank { url.substringAfterLast('/').ifBlank { "Video" } }, url, false)
                    message = null
                    showUrlDialog = false
                } else {
                    message = "Only https:// URLs are allowed (plain HTTP is blocked for safety)."
                    showUrlDialog = false
                }
            }
        )
    }
}

@Composable
private fun AddUrlDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add video URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("https://… .mp4 / .m3u8 / .mpd") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim(), url.trim()) }, enabled = url.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal fun displayName(context: android.content.Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Local video"
    } catch (_: Exception) {
        "Local video"
    }
}

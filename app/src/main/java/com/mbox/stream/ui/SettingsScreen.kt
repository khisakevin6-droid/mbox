package com.mbox.stream.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mbox.stream.AppViewModel
import com.mbox.stream.player.speedLabel

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val languages = listOf(
    "off" to "Off", "en" to "English", "es" to "Spanish", "fr" to "French",
    "de" to "German", "pt" to "Portuguese", "ar" to "Arabic", "hi" to "Hindi"
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    var url by remember(prefs.catalogUrl) { mutableStateOf(prefs.catalogUrl) }
    var tmdb by remember(prefs.tmdbKey) { mutableStateOf(prefs.tmdbKey) }
    var api by remember(prefs.sourceApiTemplate) { mutableStateOf(prefs.sourceApiTemplate) }
    var showLicenses by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Header("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (k, label) ->
                FilterChip(selected = prefs.theme == k, onClick = { vm.setTheme(k) }, label = { Text(label) })
            }
        }

        HorizontalDivider()
        Header("Playback defaults")
        Text("Default speed")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speeds.forEach { s ->
                FilterChip(selected = prefs.speed == s, onClick = { vm.setSpeed(s) }, label = { Text(s.speedLabel()) })
            }
        }
        Text("Preferred subtitle language")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (code, label) ->
                FilterChip(selected = prefs.subLang == code, onClick = { vm.setSubLang(code) }, label = { Text(label) })
            }
        }

        HorizontalDivider()
        Header("Metadata provider")
        listOf("sample" to "Sample catalogue (offline demo)", "remote" to "My catalogue URL (JSON)", "tmdb" to "TMDB (needs your API key)")
            .forEach { (k, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prefs.catalogMode == k, onClick = { vm.setCatalogMode(k) })
                    Text(label)
                }
            }
        OutlinedTextField(
            url, { url = it }, label = { Text("Catalogue URL (https)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            tmdb, { tmdb = it }, label = { Text("TMDB API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            api, { api = it }, label = { Text("Source API URL template (optional)") },
            supportingText = { Text("Example: https://example.com/api/sources?id={id}&ep={episode}") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.saveConnections(url, tmdb, api); vm.reload(); info = "Saved." }) { Text("Save and reload") }
        Text(
            "Your API key is stored only on this device and is never part of the app's source code. " +
                "TMDB supplies metadata and artwork only; this product uses the TMDB API but is not endorsed or certified by TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()
        Header("Optional modules")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = prefs.magnetEnabled, onCheckedChange = { vm.setMagnetEnabled(it) })
            Text("Magnet link tools (Library > My Videos)", Modifier.padding(start = 12.dp))
        }

        HorizontalDivider()
        Header("Data")
        OutlinedButton(onClick = { confirmClear = true }) { Text("Clear watch history") }
        OutlinedButton(onClick = { vm.clearCache(); info = "Cache cleared." }) { Text("Clear cache") }

        HorizontalDivider()
        Header("About")
        Text("MBOX version $version")
        TextButton(onClick = { showLicenses = true }) { Text("Open-source licenses") }
        info?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?") },
            text = { Text("This removes Continue Watching and history. Favorites and watchlists are kept.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false; info = "History cleared." }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "Apache License 2.0:\n• AndroidX (Compose, Navigation, Room, DataStore, Lifecycle, Activity, Core)\n" +
                        "• AndroidX Media3 (ExoPlayer, HLS, DASH, UI)\n• Coil\n• Kotlin and kotlinx.coroutines\n\n" +
                        "Demo videos: Big Buck Bunny, Sintel, Tears of Steel and Elephants Dream are Blender Foundation open movies (CC BY 3.0).",
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

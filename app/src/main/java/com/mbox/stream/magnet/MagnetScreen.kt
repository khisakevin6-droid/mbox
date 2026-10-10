package com.mbox.stream.magnet

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Optional, isolated magnet tools. Validation and metadata display are implemented.
 * In-app torrent streaming is NOT implemented; see README ("Magnet module").
 */
@Composable
fun MagnetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<MagnetResult?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Magnet tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Only use magnet links for public-domain or otherwise authorized content that you supplied yourself. " +
                "MBOX does not search for, collect or bundle magnet links.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; result = null; note = null },
            label = { Text("Magnet link") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Button(onClick = { result = MagnetParser.parse(input) }) { Text("Validate") }

        when (val r = result) {
            is MagnetResult.Invalid -> Text(r.reason, color = MaterialTheme.colorScheme.error)
            is MagnetResult.Valid -> {
                val i = r.info
                Text("Valid magnet link", fontWeight = FontWeight.Bold)
                Text("Name: ${i.displayName ?: "not provided"}")
                Text("Info-hash: ${i.infoHash}")
                Text("Trackers: ${if (i.trackers.isEmpty()) "none listed" else i.trackers.size.toString()}")
                i.trackers.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Text("Size: ${i.exactLength?.let { "$it bytes" } ?: "not provided"}")
                Text(
                    "In-app torrent playback is not implemented in this build. You can hand the link to a torrent app " +
                        "you have installed, then add the finished file under Library > My Videos > Add file.",
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.uri)))
                    } catch (_: ActivityNotFoundException) {
                        note = "No installed app can open magnet links."
                    }
                }) { Text("Open in a torrent app") }
            }
            null -> Unit
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

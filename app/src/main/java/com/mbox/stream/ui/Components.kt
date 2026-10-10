package com.mbox.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mbox.stream.domain.Title
import kotlin.math.absoluteValue

/** Remote artwork when available, otherwise a generated gradient with the title's initial. */
@Composable
fun Artwork(title: Title, modifier: Modifier = Modifier, url: String? = title.posterUrl) {
    val hue = (title.id.hashCode().absoluteValue % 360).toFloat()
    val brush = Brush.linearGradient(
        listOf(Color.hsv(hue, 0.55f, 0.38f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.14f))
    )
    Box(modifier.background(brush), contentAlignment = Alignment.Center) {
        Text(
            title.title.take(1).uppercase(),
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.55f)
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PosterCard(title: Title, onClick: () -> Unit, progress: Float? = null, modifier: Modifier = Modifier) {
    Column(modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) {
            Artwork(title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (title.demo) DemoBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        Text(
            title.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            if (title.year > 0) title.year.toString() else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    Text(
        "DEMO",
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyView(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

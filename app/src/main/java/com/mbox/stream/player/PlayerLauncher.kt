package com.mbox.stream.player

import android.content.Context
import android.content.Intent
import com.mbox.stream.domain.StreamSource

data class PlayRequest(
    val uri: String,
    val title: String,
    val titleId: String,
    val episodeKey: String,
    val mime: String?,
    val subtitle: String?,
    val speed: Float,
    val subLang: String
) {
    val entryKey: String get() = "$titleId|$episodeKey"

    companion object {
        private const val X_URI = "uri"
        private const val X_TITLE = "title"
        private const val X_TITLE_ID = "titleId"
        private const val X_EPISODE = "episode"
        private const val X_MIME = "mime"
        private const val X_SUB = "subtitle"
        private const val X_SPEED = "speed"
        private const val X_LANG = "lang"

        fun toIntent(context: Context, r: PlayRequest): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(X_URI, r.uri)
                .putExtra(X_TITLE, r.title)
                .putExtra(X_TITLE_ID, r.titleId)
                .putExtra(X_EPISODE, r.episodeKey)
                .putExtra(X_MIME, r.mime)
                .putExtra(X_SUB, r.subtitle)
                .putExtra(X_SPEED, r.speed)
                .putExtra(X_LANG, r.subLang)

        fun from(i: Intent): PlayRequest? {
            val uri = i.getStringExtra(X_URI) ?: return null
            return PlayRequest(
                uri = uri,
                title = i.getStringExtra(X_TITLE) ?: "Video",
                titleId = i.getStringExtra(X_TITLE_ID) ?: "custom",
                episodeKey = i.getStringExtra(X_EPISODE) ?: "movie",
                mime = i.getStringExtra(X_MIME),
                subtitle = i.getStringExtra(X_SUB),
                speed = i.getFloatExtra(X_SPEED, 1f),
                subLang = i.getStringExtra(X_LANG) ?: "en"
            )
        }
    }
}

object PlayerLauncher {
    fun start(
        context: Context,
        label: String,
        titleId: String,
        episodeKey: String,
        source: StreamSource,
        speed: Float,
        subLang: String
    ) {
        val request = PlayRequest(
            uri = source.url,
            title = label,
            titleId = titleId,
            episodeKey = episodeKey,
            mime = source.kind.mimeType,
            subtitle = source.subtitleUrl,
            speed = speed,
            subLang = subLang
        )
        context.startActivity(PlayRequest.toIntent(context, request))
    }
}

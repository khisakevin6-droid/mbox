package com.mbox.stream.domain

enum class TitleType { MOVIE, SERIES }

enum class MediaKind(val label: String, val mimeType: String?) {
    MP4("MP4", "video/mp4"),
    HLS("HLS", "application/x-mpegURL"),
    DASH("DASH", "application/dash+xml"),
    AUTO("Auto", null)
}

data class StreamSource(
    val providerName: String,
    val url: String,
    val kind: MediaKind = MediaKind.AUTO,
    val subtitleUrl: String? = null,
    val verified: Boolean = false,
    val note: String? = null
)

data class Episode(
    val season: Int,
    val number: Int,
    val title: String,
    val runtimeMinutes: Int,
    val sources: List<StreamSource>
) {
    val key: String get() = "s${season}e$number"
}

data class Season(val number: Int, val episodes: List<Episode>)

data class Title(
    val id: String,
    val title: String,
    val type: TitleType,
    val year: Int,
    val runtimeMinutes: Int,
    val rating: Double,
    val popularity: Int,
    val added: String,
    val synopsis: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val demo: Boolean,
    val sources: List<StreamSource>,
    val seasons: List<Season>
) {
    fun hasSources(): Boolean =
        sources.isNotEmpty() || seasons.any { s -> s.episodes.any { it.sources.isNotEmpty() } }
}

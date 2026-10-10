package com.mbox.stream.data

import android.content.Context
import com.mbox.stream.domain.CatalogRepository
import com.mbox.stream.domain.Title
import com.mbox.stream.domain.TitleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Bundled demo catalogue (assets/catalog.json). Works offline. */
class AssetCatalogRepository(private val context: Context) : CatalogRepository {
    override val name = "Sample catalogue"
    override suspend fun load(): Result<List<Title>> = runCatching {
        val text = withContext(Dispatchers.IO) {
            context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        }
        CatalogJson.parseCatalog(text)
    }
}

/** Catalogue JSON hosted by the user (same format as assets/catalog.json). */
class RemoteCatalogRepository(private val url: String) : CatalogRepository {
    override val name = "Custom catalogue URL"
    override suspend fun load(): Result<List<Title>> = runCatching {
        require(url.startsWith("https://")) { "Enter an https:// catalogue URL in Settings." }
        CatalogJson.parseCatalog(Http.get(url))
    }
}

/**
 * TMDB metadata (trending movies and series). Uses the user's own API key, stored only on the device.
 * TMDB provides metadata and artwork only - it supplies no video streams.
 */
class TmdbCatalogRepository(private val apiKey: String) : CatalogRepository {
    override val name = "TMDB"

    override suspend fun load(): Result<List<Title>> = runCatching {
        require(apiKey.isNotBlank()) { "Add your TMDB API key in Settings." }
        fetch("movie") + fetch("tv")
    }

    private suspend fun fetch(kind: String): List<Title> {
        val key = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val text = Http.get("https://api.themoviedb.org/3/trending/$kind/week?api_key=$key")
        val results = JSONObject(text).getJSONArray("results")
        return (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            val date = o.optString("release_date", "").ifBlank { o.optString("first_air_date", "") }
            val poster = o.optString("poster_path", "")
            val backdrop = o.optString("backdrop_path", "")
            val ids = o.optJSONArray("genre_ids")
            Title(
                id = "tmdb-$kind-${o.getInt("id")}",
                title = o.optString("title", "").ifBlank { o.optString("name", "Untitled") },
                type = if (kind == "tv") TitleType.SERIES else TitleType.MOVIE,
                year = date.take(4).toIntOrNull() ?: 0,
                runtimeMinutes = 0,
                rating = o.optDouble("vote_average", 0.0),
                popularity = o.optDouble("popularity", 0.0).toInt(),
                added = date,
                synopsis = o.optString("overview", ""),
                posterUrl = if (poster.isBlank() || poster == "null") null else "https://image.tmdb.org/t/p/w500$poster",
                backdropUrl = if (backdrop.isBlank() || backdrop == "null") null else "https://image.tmdb.org/t/p/w780$backdrop",
                genres = if (ids == null) emptyList() else (0 until ids.length()).mapNotNull { GENRES[ids.getInt(it)] },
                demo = false,
                sources = emptyList(),
                seasons = emptyList()
            )
        }
    }

    private companion object {
        val GENRES = mapOf(
            28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
            99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
            27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
            53 to "Thriller", 10752 to "War", 37 to "Western", 10759 to "Action", 10765 to "Sci-Fi",
            10762 to "Family", 10768 to "War"
        )
    }
}

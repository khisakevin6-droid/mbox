package com.mbox.stream.data

import com.mbox.stream.domain.Episode
import com.mbox.stream.domain.MediaKind
import com.mbox.stream.domain.Season
import com.mbox.stream.domain.StreamSource
import com.mbox.stream.domain.Title
import com.mbox.stream.domain.TitleType
import org.json.JSONArray
import org.json.JSONObject

/** Parser for the catalogue format documented in README.md (see assets/catalog.json). */
object CatalogJson {

    fun parseCatalog(text: String): List<Title> {
        val arr = JSONObject(text).getJSONArray("titles")
        return (0 until arr.length()).map { parseTitle(arr.getJSONObject(it)) }
    }

    /** Parses {"sources":[...]} as returned by a custom source API. */
    fun parseSourcesResponse(text: String): List<StreamSource> =
        parseSources(JSONObject(text).optJSONArray("sources"))

    fun parseSources(arr: JSONArray?): List<StreamSource> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StreamSource(
                providerName = o.optString("provider", "Unnamed provider"),
                url = o.getString("url"),
                kind = kindOf(o.optString("type", "auto")),
                subtitleUrl = o.optString("subtitle", "").ifBlank { null },
                verified = o.optBoolean("verified", false),
                note = o.optString("note", "").ifBlank { null }
            )
        }
    }

    private fun kindOf(s: String): MediaKind = when (s.lowercase()) {
        "mp4" -> MediaKind.MP4
        "hls", "m3u8" -> MediaKind.HLS
        "dash", "mpd" -> MediaKind.DASH
        else -> MediaKind.AUTO
    }

    private fun parseTitle(o: JSONObject): Title {
        val genres = o.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.getString(it) } }
            ?: emptyList()
        val seasonsJson = o.optJSONArray("seasons")
        val seasons = if (seasonsJson == null) emptyList() else (0 until seasonsJson.length()).map { si ->
            val s = seasonsJson.getJSONObject(si)
            val number = s.optInt("number", si + 1)
            val eps = s.optJSONArray("episodes")
            Season(
                number,
                if (eps == null) emptyList() else (0 until eps.length()).map { ei ->
                    val e = eps.getJSONObject(ei)
                    Episode(
                        season = number,
                        number = e.optInt("number", ei + 1),
                        title = e.optString("title", "Episode ${ei + 1}"),
                        runtimeMinutes = e.optInt("runtime", 0),
                        sources = parseSources(e.optJSONArray("sources"))
                    )
                }
            )
        }
        return Title(
            id = o.getString("id"),
            title = o.getString("title"),
            type = if (o.optString("type", "movie").equals("series", ignoreCase = true)) TitleType.SERIES else TitleType.MOVIE,
            year = o.optInt("year", 0),
            runtimeMinutes = o.optInt("runtime", 0),
            rating = o.optDouble("rating", 0.0),
            popularity = o.optInt("popularity", 0),
            added = o.optString("added", ""),
            synopsis = o.optString("synopsis", ""),
            posterUrl = o.optString("poster", "").ifBlank { null },
            backdropUrl = o.optString("backdrop", "").ifBlank { null },
            genres = genres,
            demo = o.optBoolean("demo", true),
            sources = parseSources(o.optJSONArray("sources")),
            seasons = seasons
        )
    }
}

package com.mbox.stream.sources

import com.mbox.stream.data.CatalogJson
import com.mbox.stream.data.Http
import com.mbox.stream.domain.Episode
import com.mbox.stream.domain.MediaKind
import com.mbox.stream.domain.StreamSource
import com.mbox.stream.domain.Title
import java.net.URLEncoder

sealed interface SourceResult {
    data class Available(val sources: List<StreamSource>) : SourceResult
    data class Unavailable(val reason: String) : SourceResult
}

data class ProviderResult(val providerName: String, val result: SourceResult)

/**
 * A provider returns sources the user is authorised to play. Implementations must never throw:
 * report problems through [SourceResult.Unavailable].
 */
interface SourceProvider {
    val displayName: String
    val supportedKinds: Set<MediaKind>
    suspend fun sourcesFor(title: Title, episode: Episode?): SourceResult
}

/** Sources listed inside the catalogue itself (public-domain, own server, demo entries). */
class CatalogSourceProvider : SourceProvider {
    override val displayName = "Catalogue sources"
    override val supportedKinds = setOf(MediaKind.MP4, MediaKind.HLS, MediaKind.DASH, MediaKind.AUTO)

    override suspend fun sourcesFor(title: Title, episode: Episode?): SourceResult {
        val list = episode?.sources ?: title.sources
        return if (list.isEmpty()) {
            SourceResult.Unavailable("No source is configured for this title. Add one to your catalogue JSON or a source provider.")
        } else {
            SourceResult.Available(list)
        }
    }
}

/**
 * Generic licensed-API / own-server provider. The user supplies a URL template containing {id}
 * (and optionally {episode}); it must answer {"sources":[ ... ]} in the catalogue source format.
 */
class HttpJsonSourceProvider(private val template: String) : SourceProvider {
    override val displayName = "Custom source API"
    override val supportedKinds = setOf(MediaKind.MP4, MediaKind.HLS, MediaKind.DASH, MediaKind.AUTO)

    override suspend fun sourcesFor(title: Title, episode: Episode?): SourceResult {
        return try {
            val url = template
                .replace("{id}", URLEncoder.encode(title.id, "UTF-8"))
                .replace("{episode}", URLEncoder.encode(episode?.key ?: "movie", "UTF-8"))
            val list = CatalogJson.parseSourcesResponse(Http.get(url))
            if (list.isEmpty()) SourceResult.Unavailable("The source API returned no sources for this title.")
            else SourceResult.Available(list)
        } catch (e: Exception) {
            SourceResult.Unavailable("Could not load sources: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

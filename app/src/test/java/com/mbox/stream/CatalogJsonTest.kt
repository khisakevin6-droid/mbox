package com.mbox.stream

import com.mbox.stream.data.CatalogJson
import com.mbox.stream.domain.MediaKind
import com.mbox.stream.domain.TitleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonTest {
    private val json = """
        {"titles":[
          {"id":"m1","title":"Movie One","type":"movie","year":2001,"runtime":90,"rating":7.5,"popularity":10,
           "added":"2026-01-01","synopsis":"A film.","genres":["Drama"],"demo":true,
           "sources":[{"provider":"Own server","url":"https://example.com/a.m3u8","type":"hls","verified":true}]},
          {"id":"s1","title":"Series One","type":"series","year":2020,"genres":[],
           "seasons":[{"number":1,"episodes":[{"number":1,"title":"Pilot","runtime":20,
             "sources":[{"provider":"P","url":"https://example.com/e1.mp4","type":"mp4"}]}]}]},
          {"id":"e1","title":"Empty","type":"movie"}
        ]}
    """.trimIndent()

    @Test
    fun parsesMoviesSeriesAndSources() {
        val titles = CatalogJson.parseCatalog(json)
        assertEquals(3, titles.size)
        val movie = titles[0]
        assertEquals(TitleType.MOVIE, movie.type)
        assertEquals(MediaKind.HLS, movie.sources[0].kind)
        assertTrue(movie.sources[0].verified)
        val series = titles[1]
        assertEquals(TitleType.SERIES, series.type)
        assertEquals("s1e1", series.seasons[0].episodes[0].key)
        assertTrue(series.hasSources())
        assertFalse(titles[2].hasSources())
    }

    @Test
    fun parsesSourceApiResponse() {
        val list = CatalogJson.parseSourcesResponse("""{"sources":[{"url":"https://example.com/x.mpd","type":"dash"}]}""")
        assertEquals(1, list.size)
        assertEquals(MediaKind.DASH, list[0].kind)
    }
}

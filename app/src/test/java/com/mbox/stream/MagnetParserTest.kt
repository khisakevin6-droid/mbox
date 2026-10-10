package com.mbox.stream

import com.mbox.stream.magnet.MagnetParser
import com.mbox.stream.magnet.MagnetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetParserTest {
    private val hex = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun validHexLinkWithMetadata() {
        val r = MagnetParser.parse("magnet:?xt=urn:btih:$hex&dn=My+Public+Domain+Film&tr=udp%3A%2F%2Ftracker.example%3A80&xl=1234")
        assertTrue(r is MagnetResult.Valid)
        val info = (r as MagnetResult.Valid).info
        assertEquals(hex, info.infoHash)
        assertEquals("My Public Domain Film", info.displayName)
        assertEquals(listOf("udp://tracker.example:80"), info.trackers)
        assertEquals(1234L, info.exactLength)
    }

    @Test
    fun validBase32Link() {
        val r = MagnetParser.parse("magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        assertTrue(r is MagnetResult.Valid)
    }

    @Test
    fun rejectsWrongPrefix() {
        assertTrue(MagnetParser.parse("http://example.com") is MagnetResult.Invalid)
    }

    @Test
    fun rejectsEmpty() {
        assertTrue(MagnetParser.parse("   ") is MagnetResult.Invalid)
    }

    @Test
    fun rejectsBadHash() {
        assertTrue(MagnetParser.parse("magnet:?xt=urn:btih:nothex") is MagnetResult.Invalid)
    }

    @Test
    fun rejectsMissingHash() {
        assertTrue(MagnetParser.parse("magnet:?dn=Only+A+Name") is MagnetResult.Invalid)
    }
}

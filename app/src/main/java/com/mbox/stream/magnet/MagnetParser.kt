package com.mbox.stream.magnet

import java.net.URLDecoder

data class MagnetInfo(
    val infoHash: String,
    val displayName: String?,
    val trackers: List<String>,
    val exactLength: Long?,
    val uri: String
)

sealed interface MagnetResult {
    data class Valid(val info: MagnetInfo) : MagnetResult
    data class Invalid(val reason: String) : MagnetResult
}

/** Pure Kotlin validation of a user-supplied magnet URI. It never touches the network. */
object MagnetParser {
    private val HEX_HASH = Regex("^[0-9a-fA-F]{40}$")
    private val BASE32_HASH = Regex("^[A-Za-z2-7]{32}$")

    fun parse(raw: String): MagnetResult {
        val text = raw.trim()
        if (text.isEmpty()) return MagnetResult.Invalid("Paste a magnet link first.")
        if (!text.startsWith("magnet:?", ignoreCase = true)) {
            return MagnetResult.Invalid("A magnet link must start with \"magnet:?\".")
        }

        val params = mutableListOf<Pair<String, String>>()
        for (part in text.substring("magnet:?".length).split('&')) {
            if (part.isBlank()) continue
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            params += decode(part.substring(0, idx)).lowercase() to decode(part.substring(idx + 1))
        }

        val xts = params.filter { it.first == "xt" || it.first.startsWith("xt.") }.map { it.second }
        val btih = xts.firstOrNull { it.startsWith("urn:btih:", ignoreCase = true) }
        if (btih == null) {
            return if (xts.any { it.startsWith("urn:btmh:", ignoreCase = true) }) {
                MagnetResult.Invalid("Only BitTorrent v2 hashes (btmh) were found; v1 info-hashes (btih) are expected.")
            } else {
                MagnetResult.Invalid("The link has no info-hash (xt=urn:btih:...).")
            }
        }

        val hash = btih.substring("urn:btih:".length)
        if (!HEX_HASH.matches(hash) && !BASE32_HASH.matches(hash)) {
            return MagnetResult.Invalid("The info-hash is not valid (expected 40 hex or 32 base32 characters).")
        }

        return MagnetResult.Valid(
            MagnetInfo(
                infoHash = if (HEX_HASH.matches(hash)) hash.lowercase() else hash.uppercase(),
                displayName = params.firstOrNull { it.first == "dn" }?.second?.ifBlank { null },
                trackers = params.filter { it.first == "tr" || it.first.startsWith("tr.") }.map { it.second },
                exactLength = params.firstOrNull { it.first == "xl" }?.second?.toLongOrNull(),
                uri = text
            )
        )
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}

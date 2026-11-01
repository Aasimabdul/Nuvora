package com.simbuild.nuvora

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Search-only YouTube Music catalogue adapter inspired by the modular provider approach
 * in InterTune. This deliberately returns public catalogue metadata only; it does not
 * extract media URLs or implement protected-stream/SABR playback.
 *
 * YouTube's InnerTube endpoint is undocumented and can change without notice.
 */
data class YouTubeMusicResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationLabel: String,
    val watchUrl: String
)

object YouTubeMusicRepository {
    private const val ENDPOINT = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20261030.01.00"

    suspend fun search(query: String, limit: Int = 20): List<YouTubeMusicResult> =
        withContext(Dispatchers.IO) {
            require(query.isNotBlank()) { "Search query cannot be blank" }
            val payload = JSONObject()
                .put("context", JSONObject().put("client", JSONObject()
                    .put("clientName", "WEB_REMIX")
                    .put("clientVersion", CLIENT_VERSION)
                    .put("hl", "en")
                    .put("gl", "US")))
                .put("query", query.trim())
                .put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
            }
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw IllegalStateException("YouTube Music search returned HTTP $status")
                val root = JSONObject(body)
                val found = mutableListOf<YouTubeMusicResult>()
                collectVideoRenderers(root, found, limit)
                found.distinctBy { it.videoId }.take(limit)
            } finally {
                connection.disconnect()
            }
        }

    private fun collectVideoRenderers(value: Any?, output: MutableList<YouTubeMusicResult>, limit: Int) {
        if (output.size >= limit || value == null) return
        when (value) {
            is JSONObject -> {
                val renderer = value.optJSONObject("musicResponsiveListItemRenderer")
                    ?: value.optJSONObject("videoRenderer")
                if (renderer != null) parseRenderer(renderer)?.let(output::add)
                if (output.size < limit) {
                    val keys = value.keys()
                    while (keys.hasNext() && output.size < limit) {
                        collectVideoRenderers(value.opt(keys.next()), output, limit)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                if (output.size >= limit) break
                collectVideoRenderers(value.opt(i), output, limit)
            }
        }
    }

    private fun parseRenderer(renderer: JSONObject): YouTubeMusicResult? {
        // YouTube Music search results often nest the ID under
        // navigationEndpoint.watchEndpoint rather than exposing renderer.videoId.
        val videoId = findVideoId(renderer)
            ?: return null

        val flexColumns = renderer.optJSONArray("flexColumns")
        val title = firstText(renderer.optJSONObject("title"))
            ?: flexColumns?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { firstText(it.optJSONObject("text")) }
            ?: renderer.optJSONObject("title")?.optString("simpleText")
            ?: return null

        val subtitle = firstText(renderer.optJSONObject("shortBylineText"))
            ?: flexColumns?.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { firstText(it.optJSONObject("text")) }
            ?: firstText(renderer.optJSONObject("longBylineText"))
            ?: "YouTube Music"
        val artist = subtitle.split(" • ", " · ", " - ").firstOrNull()?.trim().orEmpty()
            .ifBlank { "YouTube Music" }

        // YouTube Music nests artwork under thumbnail.musicThumbnailRenderer.thumbnail.
        // Use a predictable video thumbnail URL instead of a changing InnerTube
        // thumbnail tree; this works for search cards, recent history and the player.
        val artwork = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val duration = renderer.optJSONObject("lengthText")?.optString("simpleText")
            ?: renderer.optJSONObject("lengthText")?.let(::firstText)
            ?: ""
        return YouTubeMusicResult(
            videoId = videoId,
            title = title,
            artist = artist,
            artworkUrl = artwork,
            durationLabel = duration,
            watchUrl = "https://music.youtube.com/watch?v=$videoId"
        )
    }


    private fun findVideoId(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                val direct = value.optString("videoId")
                    .takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                if (direct != null) return direct
                val keys = value.keys()
                while (keys.hasNext()) {
                    val found = findVideoId(value.opt(keys.next()))
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val found = findVideoId(value.opt(i))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun firstText(node: JSONObject?): String? {
        if (node == null) return null
        node.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return null
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }.trim().takeIf { it.isNotBlank() }
    }
}

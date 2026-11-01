package com.simbuild.nuvora

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Best-effort public YouTube Music playlist search. This uses undocumented InnerTube endpoints. */
data class YouTubeMusicPlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val url: String
)

object YouTubeMusicPlaylistsRepository {
    private const val SEARCH_ENDPOINT = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val BROWSE_ENDPOINT = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20261030.01.00"
    private const val PLAYLIST_PARAMS = "Eg-KAQwIABAAGAAgACgB"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

    suspend fun searchPlaylists(query: String, limit: Int = 20): List<YouTubeMusicPlaylist> =
        withContext(Dispatchers.IO) {
            require(query.isNotBlank()) { "Enter a playlist name to search." }
            val payload = JSONObject()
                .put("context", context())
                .put("query", query.trim())
                .put("params", PLAYLIST_PARAMS)
            val root = request(SEARCH_ENDPOINT, payload)
            val output = mutableListOf<YouTubeMusicPlaylist>()
            collectPlaylists(root, output, limit)
            output.distinctBy { it.id }.take(limit)
        }

    suspend fun getPlaylistTracks(playlistId: String, limit: Int = 100): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val browseId = when {
                playlistId.startsWith("VL") -> playlistId
                playlistId.startsWith("PL") -> "VL$playlistId"
                else -> playlistId
            }
            val root = request(BROWSE_ENDPOINT, JSONObject()
                .put("context", context())
                .put("browseId", browseId))
            val output = mutableListOf<MusicTrack>()
            collectTracks(root, output, limit)
            output.distinctBy { it.id }.take(limit)
        }

    private fun context() = JSONObject()
        .put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US"))

    private fun request(endpoint: String, payload: JSONObject): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 18_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Origin", "https://music.youtube.com")
            setRequestProperty("Referer", "https://music.youtube.com/")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("YouTube Music playlist request returned HTTP $status")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun collectPlaylists(value: Any?, output: MutableList<YouTubeMusicPlaylist>, limit: Int) {
        if (value == null || output.size >= limit) return
        when (value) {
            is JSONObject -> {
                val renderer = value.optJSONObject("musicTwoRowItemRenderer")
                    ?: value.optJSONObject("musicResponsiveListItemRenderer")
                    ?: value.optJSONObject("playlistRenderer")
                if (renderer != null) parsePlaylist(renderer)?.let { candidate ->
                    if (output.none { it.id == candidate.id }) output.add(candidate)
                }
                if (output.size < limit) {
                    val keys = value.keys()
                    while (keys.hasNext() && output.size < limit) {
                        collectPlaylists(value.opt(keys.next()), output, limit)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                if (output.size >= limit) break
                collectPlaylists(value.opt(i), output, limit)
            }
        }
    }

    private fun parsePlaylist(renderer: JSONObject): YouTubeMusicPlaylist? {
        val id = findPlaylistId(renderer) ?: return null
        val flex = renderer.optJSONArray("flexColumns")
        val title = text(renderer.optJSONObject("title"))
            ?: flex?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { text(it.optJSONObject("text")) }
            ?: return null
        val subtitle = text(renderer.optJSONObject("subtitle"))
            ?: text(renderer.optJSONObject("shortBylineText"))
            ?: flex?.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { text(it.optJSONObject("text")) }
            ?: "YouTube Music playlist"
        val thumbnailArray = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val artwork = thumbnailArray?.let { array ->
            (array.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
                normalizeArtworkUrl(array.optJSONObject(index)?.optString("url"))
            }
        }
        val listId = id.removePrefix("VL")
        return YouTubeMusicPlaylist(
            id = id,
            title = title,
            subtitle = subtitle,
            artworkUrl = artwork,
            url = "https://music.youtube.com/playlist?list=$listId"
        )
    }

    private fun findPlaylistId(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                listOf("browseId", "playlistId").forEach { key ->
                    val id = value.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    if (id != null && (id.startsWith("VLPL") || id.startsWith("PL"))) return id
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val found = findPlaylistId(value.opt(keys.next()))
                    if (found != null) return found
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                val found = findPlaylistId(value.opt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun collectTracks(value: Any?, output: MutableList<MusicTrack>, limit: Int) {
        if (value == null || output.size >= limit) return
        when (value) {
            is JSONObject -> {
                val renderer = value.optJSONObject("musicResponsiveListItemRenderer")
                    ?: value.optJSONObject("playlistVideoRenderer")
                    ?: value.optJSONObject("videoRenderer")
                if (renderer != null) parseTrack(renderer)?.let { candidate ->
                    if (output.none { it.id == candidate.id }) output.add(candidate)
                }
                if (output.size < limit) {
                    val keys = value.keys()
                    while (keys.hasNext() && output.size < limit) {
                        collectTracks(value.opt(keys.next()), output, limit)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                if (output.size >= limit) break
                collectTracks(value.opt(i), output, limit)
            }
        }
    }

    private fun parseTrack(renderer: JSONObject): MusicTrack? {
        val videoId = findVideoId(renderer) ?: return null
        val flex = renderer.optJSONArray("flexColumns")
        val title = text(renderer.optJSONObject("title"))
            ?: flex?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { text(it.optJSONObject("text")) }
            ?: return null
        val artist = text(renderer.optJSONObject("shortBylineText"))
            ?: flex?.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { text(it.optJSONObject("text")) }
            ?: "YouTube Music"
        val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val artwork = thumbnails?.let { array ->
            (array.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
                normalizeArtworkUrl(array.optJSONObject(index)?.optString("url"))
            }
        } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val durationLabel = text(renderer.optJSONObject("lengthText")).orEmpty()
        val duration = durationLabel.split(":").mapNotNull { it.toIntOrNull() }
            .fold(0) { total, part -> total * 60 + part }
        val watchUrl = "https://music.youtube.com/watch?v=$videoId"
        return MusicTrack(
            id = "youtube-$videoId",
            title = title,
            artist = artist.substringBefore(" • ").substringBefore(" · ").ifBlank { "YouTube Music" },
            artworkUrl = artwork,
            durationSeconds = duration,
            permalink = watchUrl,
            streamUrl = watchUrl
        )
    }

    private fun findVideoId(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                val candidate = value.optString("videoId")
                    .takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                if (candidate != null) return candidate
                val keys = value.keys()
                while (keys.hasNext()) {
                    val found = findVideoId(value.opt(keys.next()))
                    if (found != null) return found
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                val found = findVideoId(value.opt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun text(node: JSONObject?): String? {
        if (node == null) return null
        node.optString("simpleText").takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return null
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }.trim().takeIf { it.isNotBlank() }
    }
}

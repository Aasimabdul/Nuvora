package com.simbuild.nuvora

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Community JioSaavn search adapter. This depends on an unofficial public API;
 * endpoints and stream availability can change without notice.
 */
object JioSaavnRepository {
    private const val BASE_URL = "https://saavn.sumit.co/api"

    suspend fun searchTracks(query: String, limit: Int = 25): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query.ifBlank { "music" }, "UTF-8")
            val json = getJson("$BASE_URL/search/songs?query=$encoded&page=0")
            val root = json.optJSONObject("data") ?: json
            val results = root.optJSONArray("results")
                ?: root.optJSONArray("songs")
                ?: json.optJSONArray("results")
                ?: return@withContext emptyList()

            buildList {
                for (i in 0 until minOf(results.length(), limit)) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val title = item.optString("name").takeIf { it.isNotBlank() && it != "null" }
                        ?: item.optString("title", "Untitled track")
                    val artistsObject = item.optJSONObject("artists")
                    val primaryArtists = artistsObject?.optJSONArray("primary")
                    val artist = if (primaryArtists != null && primaryArtists.length() > 0) {
                        (0 until primaryArtists.length()).mapNotNull { idx ->
                            primaryArtists.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
                        }.joinToString(", ")
                    } else {
                        item.optString("primaryArtists").takeIf { it.isNotBlank() && it != "null" }
                            ?: item.optString("artist", "Unknown artist")
                    }
                    val artwork = firstUrl(item.optJSONArray("image"))
                        ?: item.optString("image").takeIf { it.startsWith("https://") }
                    val downloads = item.optJSONArray("downloadUrl")
                        ?: item.optJSONArray("download_url")
                    val stream = if (downloads != null) {
                        (0 until downloads.length()).mapNotNull { idx ->
                            val entry = downloads.optJSONObject(idx) ?: return@mapNotNull null
                            val url = entry.optString("url").takeIf { it.startsWith("https://") }
                                ?: entry.optString("link").takeIf { it.startsWith("https://") }
                            val quality = entry.optString("quality").replace("kbps", "").toIntOrNull() ?: 0
                            if (url == null) null else quality to url
                        }.maxByOrNull { it.first }?.second
                    } else null
                    if (stream.isNullOrBlank()) continue
                    val duration = item.optInt("duration", 0)
                    val permalink = item.optString("url").takeIf { it.startsWith("https://") }
                        ?: "https://www.jiosaavn.com"
                    add(
                        MusicTrack(
                            id = "jiosaavn-$id",
                            title = title,
                            artist = artist.ifBlank { "Unknown artist" },
                            artworkUrl = artwork,
                            durationSeconds = duration,
                            permalink = permalink,
                            streamUrl = stream
                        )
                    )
                }
            }
        }

    private fun firstUrl(items: org.json.JSONArray?): String? {
        if (items == null) return null
        for (i in items.length() - 1 downTo 0) {
            val item = items.optJSONObject(i) ?: continue
            val url = item.optString("url").takeIf { it.startsWith("https://") }
                ?: item.optString("link").takeIf { it.startsWith("https://") }
            if (url != null) return url
        }
        return null
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Nuvora-Android/0.3.8")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("JioSaavn API returned HTTP $status")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}

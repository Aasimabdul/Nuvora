package com.simbuild.nuvora

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Audius public catalogue client. Uses the official public API and stream endpoint.
 * Tracks remain subject to the rights and availability settings chosen by their creators.
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSeconds: Int,
    val permalink: String,
    val streamUrl: String
) {
    val durationLabel: String
        get() = "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
}

object AudiusRepository {
    private const val BASE_URL = "https://api.audius.co/v1"
    private const val APP_NAME = "Nuvora"

    suspend fun searchTracks(query: String, limit: Int = 25): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query.ifBlank { "electronic chill" }, "UTF-8")
            val url = "$BASE_URL/tracks/search?query=$encoded&limit=$limit&app_name=$APP_NAME"
            val json = getJson(url)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val user = item.optJSONObject("user")
                    val artwork = item.optJSONObject("artwork")
                    val artworkUrl = artwork?.optString("150x150")?.takeIf { it.startsWith("https://") }
                        ?: artwork?.optString("480x480")?.takeIf { it.startsWith("https://") }
                    val duration = item.optInt("duration", 0)
                    val permalink = item.optString("permalink").takeIf { it.startsWith("https://") }
                        ?: "https://audius.co"
                    add(
                        MusicTrack(
                            id = id,
                            title = item.optString("title", "Untitled track"),
                            artist = user?.optString("name")?.takeIf { it.isNotBlank() && it != "null" } ?: "Unknown artist",
                            artworkUrl = artworkUrl,
                            durationSeconds = duration,
                            permalink = permalink,
                            streamUrl = "$BASE_URL/tracks/$id/stream?app_name=$APP_NAME"
                        )
                    )
                }
            }
        }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Nuvora-Android/0.1")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Music service returned HTTP $status")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}

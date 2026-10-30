package com.simbuild.nuvora

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Jamendo's public catalogue adapter.
 *
 * Register an app at https://devportal.jamendo.com/ and supply its own client_id.
 * Jamendo's API terms currently restrict free API use to non-commercial projects.
 * Respect the licence displayed for each track and keep attribution visible.
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationSeconds: Int,
    val licenseUrl: String
)

class JamendoRepository(
    private val clientId: String
) {
    suspend fun searchTracks(query: String, limit: Int = 30): List<MusicTrack> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(clientId.isNotBlank() && clientId != "YOUR_JAMENDO_CLIENT_ID") {
                "Add your Jamendo client ID before searching."
            }

            val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            val endpoint = "https://api.jamendo.com/v3.0/tracks/" +
                "?client_id=${URLEncoder.encode(clientId, Charsets.UTF_8.name())}" +
                "&format=json&limit=${limit.coerceIn(1, 50)}&audioformat=mp31" +
                "&include=licenses&order=popularity_total_desc" +
                if (query.isBlank()) "" else "&search=$encodedQuery"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
            }

            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (status !in 200..299) {
                    throw IllegalStateException("Music catalogue request failed (HTTP $status).")
                }

                val root = JSONObject(body)
                val headers = root.optJSONObject("headers")
                if (headers != null && headers.optString("status") != "success") {
                    throw IllegalStateException(headers.optString("error_message", "Music catalogue request failed."))
                }

                val results = root.optJSONArray("results") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val audio = item.optString("audio")
                        if (audio.isBlank() || audio == "null") continue
                        add(
                            MusicTrack(
                                id = item.optString("id"),
                                title = item.optString("name", "Unknown track"),
                                artist = item.optString("artist_name", "Unknown artist"),
                                album = item.optString("album_name", ""),
                                artworkUrl = item.optString("image"),
                                streamUrl = audio,
                                durationSeconds = item.optInt("duration", 0),
                                licenseUrl = item.optString("license_ccurl")
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
}

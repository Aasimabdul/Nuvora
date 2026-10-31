package com.simbuild.nuvora

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves a public YouTube/YouTube Music video to a direct audio stream for Media3.
 *
 * NewPipe Extractor is an unofficial, open-source extractor. YouTube can change its
 * endpoints, and some videos may be unavailable, region-restricted, age-restricted,
 * or require sign-in. This component does not attempt to bypass DRM or authentication.
 */
object YouTubeMusicPlaybackRepository {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var initialized = false
    private val initLock = Any()

    suspend fun resolveAudioStream(videoId: String): String = withContext(Dispatchers.IO) {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid YouTube video ID." }
        ensureInitialized()

        val extractor = ServiceList.YouTube.getStreamExtractor(
            "https://www.youtube.com/watch?v=$videoId"
        )
        extractor.fetchPage()

        val stream = extractor.audioStreams
            .asSequence()
            .filter { it.isUrl && it.content.startsWith("https://") }
            .maxByOrNull { it.averageBitrate }
            ?: throw IOException(
                "YouTube did not provide a compatible direct audio stream for this video."
            )

        stream.content
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            NewPipe.init(NuvoraExtractorDownloader(), Localization("en", "US"))
            initialized = true
        }
    }

    private class NuvoraExtractorDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val connection = URL(request.url()).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = request.httpMethod()
                connection.connectTimeout = 20_000
                connection.readTimeout = 25_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

                request.headers().forEach { (name, values) ->
                    values.forEach { value -> connection.setRequestProperty(name, value) }
                }

                val payload = request.dataToSend()
                if (payload != null && request.httpMethod().equals("POST", ignoreCase = true)) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(payload) }
                }

                val status = connection.responseCode
                val responseStream = if (status in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseBody = responseStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                val responseHeaders: Map<String, List<String>> = connection.headerFields.entries
                    .filter { it.key != null }
                    .associate { entry -> entry.key!! to (entry.value ?: emptyList()) }

                return Response(
                    status,
                    connection.responseMessage.orEmpty(),
                    responseHeaders,
                    responseBody,
                    connection.url.toString()
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}

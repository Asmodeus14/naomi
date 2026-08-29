package com.naomi.web.impl

import com.naomi.web.PageContent
import com.naomi.web.WebReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a page over HTTPS and reduces it to title and readable text.
 *
 * Written against `HttpURLConnection` rather than adding an HTTP client. A
 * networking dependency is exactly the kind of thing that arrives with a
 * telemetry uploader attached — that is how INTERNET got into this app in the
 * first place — so the module that is allowed to open sockets brings in nothing
 * it does not need.
 *
 * What is deliberately absent is as important as what is here: no cookie jar, no
 * redirect to a different host without noticing, no custom headers identifying
 * the user or the app beyond a plain agent string, and no cache written to disk.
 * A request carries the URL and nothing else, because the URL is all this class
 * is ever given.
 */
class HttpWebReader : WebReader {

    override val isAvailable: Boolean = true

    override suspend fun read(url: String): PageContent? = withContext(Dispatchers.IO) {
        val target = runCatching { URL(url) }.getOrNull() ?: return@withContext null

        // Plain http would let anything between the user and the site read what
        // they are reading. If a link is not https, it is not fetched.
        if (!target.protocol.equals("https", ignoreCase = true)) return@withContext null

        withTimeoutOrNull(TIMEOUT_MS) {
            var connection: HttpURLConnection? = null
            try {
                connection = (target.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = TIMEOUT_MS.toInt()
                    readTimeout = TIMEOUT_MS.toInt()
                    setRequestProperty("Accept", "text/html,text/plain")
                    setRequestProperty("User-Agent", USER_AGENT)
                    useCaches = false
                }

                if (connection.responseCode !in 200..299) return@withTimeoutOrNull null

                val type = connection.contentType.orEmpty()
                if (!type.startsWith("text/")) return@withTimeoutOrNull null

                // Bounded read: a page is worth keeping only for what a person
                // would read, and an unbounded stream is a way to be handed a
                // gigabyte by a hostile host.
                val html = connection.inputStream.bufferedReader()
                    .use { reader ->
                        val buffer = CharArray(MAX_BYTES)
                        val read = reader.read(buffer, 0, MAX_BYTES)
                        if (read <= 0) "" else String(buffer, 0, read)
                    }

                val text = Html.toReadableText(html)
                if (text.isBlank()) return@withTimeoutOrNull null

                PageContent(
                    url = url,
                    title = Html.titleOf(html).ifBlank { target.host },
                    text = text
                )
            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Enough to be a good citizen in a server log; identifies nobody. */
        const val USER_AGENT = "Naomi/0.1 (+https://github.com/Asmodeus14/naomi)"

        /** ~200k characters is far more than any article and a hard ceiling. */
        const val MAX_BYTES = 200_000
    }
}

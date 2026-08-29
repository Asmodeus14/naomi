package com.naomi.web

/**
 * Everything Naomi is allowed to ask the internet for.
 *
 * This module is plain Kotlin with no dependencies. It cannot see Room, the
 * domain models, or anything else in `:app` — not by convention but because the
 * build graph points the other way, so a line of code that reached for a memory
 * here would not compile.
 *
 * That is the whole point. The privacy boundary in §4 says private context and
 * public context must not mix, and the only way to make that check-able rather
 * than promise-able is to give the networked code no vocabulary for private
 * things. [read] takes a URL and returns a page. There is no overload that takes
 * a transcript, a memory, a topic or a search over any of them, and none can be
 * added without a reviewer seeing this file change.
 */
interface WebReader {

    /**
     * Fetches [url] and returns its readable content.
     *
     * @return the page, or null if it could not be fetched or read.
     */
    suspend fun read(url: String): PageContent?

    /**
     * Whether this build can reach the network at all.
     *
     * False in the offline flavour, where there is no implementation and the
     * APK declares no INTERNET permission. Callers use this to decide whether to
     * offer the action, rather than offering it and failing.
     */
    val isAvailable: Boolean
}

/**
 * The readable part of a fetched page. Deliberately small: a title and text, no
 * headers, cookies, redirect chain or anything else that could carry identity
 * back across the boundary.
 */
data class PageContent(
    val url: String,
    val title: String,
    val text: String
)

/**
 * The implementation used by the offline flavour.
 *
 * Not a stub for testing — this is what ships by default. The build it belongs
 * to has no INTERNET permission, so the honest behaviour is to report that
 * reading is unavailable rather than to fail at the socket.
 */
object UnavailableWebReader : WebReader {
    override suspend fun read(url: String): PageContent? = null
    override val isAvailable: Boolean = false
}

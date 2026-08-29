package com.naomi.app.ai.intelligence

/**
 * Separates a shared link from the words around it.
 *
 * The understanding layer is built for speech. A URL run through it is scored as
 * if it were a sentence, which produces titles like "Https Doc Rust" and buries
 * the one thing that made the link worth keeping. So the link comes out first
 * and is stored as the memory's source; only prose goes on to be understood.
 *
 * When there is no prose — a bare URL, which is what most share sheets send —
 * the slug is used, because it is nearly always the article's title with hyphens
 * in it. That is a guess about formatting, not about meaning: every word handed
 * on appears verbatim in the link the user shared.
 */
object SharedTextParser {

    data class Shared(
        /** What the understanding layer should read. */
        val text: String,
        /** The link, kept so the memory can be opened again. */
        val url: String?
    )

    private val urlPattern = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** Path segments that are numbering rather than title: "ch04", "01", "v2". */
    private val numbering = Regex("""^[a-z]{0,2}\d+$""", RegexOption.IGNORE_CASE)

    private val fileExtension = Regex("""\.(html?|php|aspx?|jsp|md)$""", RegexOption.IGNORE_CASE)

    fun parse(subject: String, body: String): Shared {
        val combined = listOf(subject.trim(), body.trim())
            .filter { it.isNotBlank() }
            .joinToString(". ")

        val url = urlPattern.find(combined)?.value?.trimEnd('.', ',', ')')

        val prose = urlPattern.replace(combined, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.', '-', '–', '|', ':')
            .trim()

        if (prose.isNotBlank()) return Shared(prose, url)
        if (url == null) return Shared(combined, null)

        return Shared(describe(url), url)
    }

    /**
     * A readable phrase for a link that arrived with no words: the title-ish part
     * of the path, falling back to the host.
     */
    private fun describe(url: String): String {
        val withoutScheme = url.substringAfter("://")
        val host = withoutScheme.substringBefore('/').removePrefix("www.")
        val path = withoutScheme.substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')

        val slugWords = path.split('/')
            .lastOrNull { it.isNotBlank() }
            ?.let { fileExtension.replace(it, "") }
            ?.split('-', '_', '+')
            ?.filter { it.isNotBlank() && !numbering.matches(it) }
            .orEmpty()

        return if (slugWords.isEmpty()) host else slugWords.joinToString(" ")
    }
}

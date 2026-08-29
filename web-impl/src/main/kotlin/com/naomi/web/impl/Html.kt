package com.naomi.web.impl

/**
 * Just enough HTML handling to get readable prose out of a page.
 *
 * Not a parser and not trying to be. A real one would be a dependency, and this
 * module's whole justification is that the code allowed to open sockets stays
 * small enough to read in one sitting.
 */
internal object Html {

    private val titleTag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Elements whose contents are code or styling, never prose. */
    private val nonContent = Regex(
        """<(script|style|noscript|svg|head)[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val blockBreak = Regex("""</(p|div|section|article|h[1-6]|li|tr|blockquote)>""", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("""<[^>]+>""")
    private val manyBlankLines = Regex("""\n{3,}""")
    private val trailingSpace = Regex("""[ \t]+\n""")

    private val entities = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&mdash;" to "—",
        "&ndash;" to "–", "&hellip;" to "…", "&rsquo;" to "'", "&lsquo;" to "'",
        "&ldquo;" to "\"", "&rdquo;" to "\""
    )

    fun titleOf(html: String): String =
        titleTag.find(html)?.groupValues?.get(1)?.let { decode(it).trim() }.orEmpty()

    fun toReadableText(html: String): String {
        var text = nonContent.replace(html, " ")
        // Block ends become line breaks first, so paragraphs do not run together
        // into one wall of text once the tags are gone.
        text = blockBreak.replace(text, "\n")
        text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        text = anyTag.replace(text, "")
        text = decode(text)
        text = text.replace(' ', ' ')
        text = text.lines().joinToString("\n") { it.trim() }
        text = trailingSpace.replace(text, "\n")
        text = manyBlankLines.replace(text, "\n\n")
        return text.trim()
    }

    private fun decode(input: String): String {
        var out = input
        for ((entity, replacement) in entities) {
            out = out.replace(entity, replacement, ignoreCase = true)
        }
        // Numeric entities, decimal only; hex is rare enough in prose to skip
        // rather than to get subtly wrong.
        return Regex("""&#(\d{1,6});""").replace(out) { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) } ?: match.value
        }
    }
}

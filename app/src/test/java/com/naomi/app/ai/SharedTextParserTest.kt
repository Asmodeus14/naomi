package com.naomi.app.ai

import com.naomi.app.ai.intelligence.SharedTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The link must always survive, and it must never reach the understanding layer
 * — a URL scored as if it were a sentence produces a memory titled after its
 * scheme.
 */
class SharedTextParserTest {

    @Test
    fun `the link is kept and the words are what get understood`() {
        val shared = SharedTextParser.parse(
            subject = "Rust ownership model",
            body = "https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html"
        )
        assertEquals("Rust ownership model", shared.text)
        assertEquals("https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html", shared.url)
    }

    @Test
    fun `no url ever reaches the text handed on for understanding`() {
        val shared = SharedTextParser.parse(
            subject = "",
            body = "Worth reading https://example.com/a later this week"
        )
        assertFalse(shared.text.contains("http"))
        assertEquals("Worth reading later this week", shared.text)
    }

    @Test
    fun `a bare link is described by its slug`() {
        // What most share sheets actually send. "ch04" and "01" are numbering,
        // not title, so they are dropped.
        val shared = SharedTextParser.parse(
            subject = "",
            body = "https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html"
        )
        assertEquals("what is ownership", shared.text)
    }

    @Test
    fun `a link with no usable path falls back to the host`() {
        assertEquals("example.com", SharedTextParser.parse("", "https://www.example.com/").text)
        assertEquals("example.com", SharedTextParser.parse("", "https://example.com").text)
    }

    @Test
    fun `query strings and fragments are not part of the description`() {
        val shared = SharedTextParser.parse("", "https://example.com/some-good-article?utm_source=x#top")
        assertEquals("some good article", shared.text)
    }

    @Test
    fun `plain shared text is passed through untouched`() {
        val shared = SharedTextParser.parse("", "The fence waits on the gpu before present")
        assertEquals("The fence waits on the gpu before present", shared.text)
        assertNull(shared.url)
    }

    @Test
    fun `trailing punctuation is not swallowed into the link`() {
        // "(see https://example.com/page)." would otherwise store a URL ending
        // in ")." and open a 404 when tapped.
        val shared = SharedTextParser.parse("", "See https://example.com/page.")
        assertEquals("https://example.com/page", shared.url)
    }
}

package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewTest {

    @Test fun firstUrlTrimsPunctuation() {
        assertEquals("https://example.com/a?b=1", LinkExtractor.firstUrl("شوف https://example.com/a?b=1, تمام"))
        assertEquals("https://example.com/x", LinkExtractor.firstUrl("(https://example.com/x)"))
    }

    @Test fun noUrl() {
        assertNull(LinkExtractor.firstUrl("ما في رابط"))
        assertNull(LinkExtractor.firstUrl("http://a"))
    }

    @Test fun host() {
        assertEquals("example.com", LinkExtractor.hostOf("https://www.example.com/p"))
    }

    @Test fun parsesOpenGraphInEitherAttributeOrder() {
        val html = """<html><head><title>Fallback</title>
            <meta property="og:title" content="عنوان &amp; اختبار">
            <meta content="/img/a.png" property="og:image"></head></html>"""
        val m = HtmlMeta.parse(html, "https://example.com/page/x")
        assertNotNull(m)
        assertEquals("عنوان & اختبار", m!!.title)
        assertEquals("https://example.com/img/a.png", m.imageUrl)
        assertEquals("example.com", m.host)
    }

    @Test fun fallsBackToTitleTag() {
        val m = HtmlMeta.parse("<title> Hello \n World </title>", "https://example.com/")
        assertEquals("Hello World", m!!.title)
        assertNull(m.imageUrl)
    }

    @Test fun noTitleNoPreview() {
        assertNull(HtmlMeta.parse("<html><body>x</body></html>", "https://example.com/"))
    }

    @Test fun entities() {
        assertEquals("AB", HtmlMeta.decodeEntities("&#65;&#x42;"))
    }
}

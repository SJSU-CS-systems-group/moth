package edu.sjsu.moth.util;

import edu.sjsu.moth.server.util.HtmlSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HtmlSanitizerTest {
    @Test
    public void testScriptAndEventHandlersStripped() {
        var out = HtmlSanitizer.sanitize("<p>hi</p><script>alert(1)</script><img src=x onerror=pwn()>");
        assertTrue(out.contains("<p>hi</p>"));
        assertFalse(out.contains("<script"));
        assertFalse(out.contains("alert(1)"));
        assertFalse(out.contains("onerror"));
    }

    @Test
    public void testJavascriptUrlStripped() {
        var out = HtmlSanitizer.sanitize("<a href=\"javascript:steal()\">x</a>");
        assertFalse(out.contains("javascript:"));
    }

    @Test
    public void testMastodonStyleContentSurvives() {
        var in = "<p>Hello <span class=\"h-card\"><a href=\"https://example.com/@bob\" class=\"u-url mention\">" +
                "@bob</a></span> check <a href=\"https://example.com/post\">this</a></p>";
        var out = HtmlSanitizer.sanitize(in);
        assertTrue(out.contains("<p>"));
        assertTrue(out.contains("@bob"));
        assertTrue(out.contains("href=\"https://example.com/post\""));
        assertTrue(out.contains("rel=\"") && out.contains("nofollow") && out.contains("noopener") &&
                           out.contains("noreferrer"), "links must carry rel protections: " + out);
    }

    @Test
    public void testPlainTextPassesThrough() {
        assertEquals("hello world @bob #tag", HtmlSanitizer.sanitize("hello world @bob #tag"));
    }

    @Test
    public void testNullSafe() {
        assertNull(HtmlSanitizer.sanitize(null));
        assertNull(HtmlSanitizer.stripHtml(null));
    }

    @Test
    public void testStripHtml() {
        assertEquals("alert(1)bold name", HtmlSanitizer.stripHtml("<script>alert(1)</script><b>bold</b> name"));
    }
}

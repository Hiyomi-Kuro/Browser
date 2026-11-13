package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.agent.UrlTools;

import org.junit.Test;

/** URL policy used before any navigation. */
public class UrlToolsTest {

    @Test
    public void onlyHttpAndHttpsAreOpenable() {
        assertTrue(UrlTools.isHttpUrl("https://example.com"));
        assertTrue(UrlTools.isHttpUrl("http://example.com/a?b=1"));
        assertFalse(UrlTools.isHttpUrl("javascript:alert(1)"));
        assertFalse(UrlTools.isHttpUrl("file:///etc/hosts"));
        assertFalse(UrlTools.isHttpUrl("example.com"));
        assertFalse(UrlTools.isHttpUrl(""));
    }

    @Test
    public void dangerousSchemesAreFlagged() {
        assertTrue(UrlTools.isDangerousScheme("javascript:void(0)"));
        assertTrue(UrlTools.isDangerousScheme("FILE:///x"));
        assertTrue(UrlTools.isDangerousScheme("content://x"));
        assertTrue(UrlTools.isDangerousScheme("data:text/html,<b>x</b>"));
        assertTrue(UrlTools.isDangerousScheme("intent://x"));
        assertTrue(UrlTools.isDangerousScheme("about:blank"));
        assertFalse(UrlTools.isDangerousScheme("https://example.com"));
    }

    @Test
    public void normalisesMissingScheme() {
        assertEquals("https://example.com", UrlTools.ensureScheme("example.com"));
        assertEquals("http://example.com", UrlTools.ensureScheme("http://example.com"));
        assertEquals("", UrlTools.ensureScheme(""));
    }

    @Test
    public void dedupeKeyIgnoresFragmentAndCase() {
        assertEquals(UrlTools.normalizeForDedupe("https://Example.com/a/"),
                UrlTools.normalizeForDedupe("https://example.com/a#section"));
    }

    @Test
    public void detectsSameSite() {
        assertTrue(UrlTools.sameSite("https://news.example.com/a", "https://example.com/b"));
        assertFalse(UrlTools.sameSite("https://example.com", "https://other.example"));
        assertEquals("example.com", UrlTools.hostOf("https://example.com/x"));
        assertEquals("", UrlTools.hostOf("not a url"));
    }
}

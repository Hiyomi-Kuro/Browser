package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.agent.tools.ElementIds;
import com.kaori.browser.ai.research.ResearchOptions;
import com.kaori.browser.ai.storage.FileNames;

import org.junit.Test;

/** Filenames, element ids and research limit clamping. */
public class StorageAndLimitsTest {

    @Test
    public void elementIdsAreStrictlyValidated() {
        assertTrue(ElementIds.isValid("e1"));
        assertTrue(ElementIds.isValid("e1234"));
        assertFalse(ElementIds.isValid("e"));
        assertFalse(ElementIds.isValid("element1"));
        assertFalse(ElementIds.isValid("e1;alert(1)"));
        assertFalse(ElementIds.isValid(""));
        assertFalse(ElementIds.isValid(null));
    }

    @Test
    public void fileNamesAreSanitised() {
        assertEquals("a_b_c.md", FileNames.normalize("a/b:c", ".md"));
        assertEquals("AI export.md", FileNames.normalize("   ", ".md"));
        assertEquals("report.md", FileNames.normalize("report", "md"));
        String longName = FileNames.normalize(repeat("x", 200), ".md");
        assertTrue(longName.length() <= 84);
        assertTrue(longName.endsWith(".md"));
    }

    @Test
    public void extractsNameFromUrl() {
        assertEquals("paper.pdf", FileNames.fromUrl("https://example.com/x/paper.pdf?a=1"));
        assertEquals("", FileNames.fromUrl("https://example.com/"));
        assertEquals("", FileNames.fromUrl(null));
    }

    @Test
    public void researchOptionsTreatZeroAsUnlimited() throws Exception {
        ResearchOptions unlimited = ResearchOptions.unlimited("简体中文");
        assertEquals(0, unlimited.maxResults);
        assertEquals(0, unlimited.maxPages);
        assertEquals(0, unlimited.maxDepth);
        assertEquals(0, unlimited.timeoutSeconds);
        assertEquals(0, unlimited.maxSteps);
        assertFalse(unlimited.hasStepLimit());
        assertFalse(unlimited.hasTimeLimit());
        assertTrue(unlimited.describeLimits().contains("不限制"));
        assertEquals("简体中文", unlimited.reportLanguage);
    }

    @Test
    public void researchOptionsClampNegativesAndDefaultLanguage() throws Exception {
        ResearchOptions options = new ResearchOptions(-5, -1, 0, 0, 0, "   ");
        assertEquals(0, options.maxResults);
        assertEquals(0, options.maxPages);
        assertEquals(ResearchOptions.DEFAULT_REPORT_LANGUAGE, options.reportLanguage);
    }

    @Test
    public void researchOptionsRoundTripThroughJson() {
        ResearchOptions options = new ResearchOptions(3, 4, 2, 300, 12, "English");
        ResearchOptions restored = ResearchOptions.fromJson(options.toJson());
        assertEquals(options.maxResults, restored.maxResults);
        assertEquals(options.maxPages, restored.maxPages);
        assertEquals(options.maxDepth, restored.maxDepth);
        assertEquals(options.timeoutSeconds, restored.timeoutSeconds);
        assertEquals(options.maxSteps, restored.maxSteps);
        assertEquals("English", restored.reportLanguage);
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}

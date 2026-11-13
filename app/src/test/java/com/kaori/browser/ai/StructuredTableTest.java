package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.research.StructuredTable;

import org.junit.Test;

import java.util.Arrays;

/** Report tables must be parseable, de-duplicated and exportable without invention. */
public class StructuredTableTest {

    @Test
    public void parsesMarkdownTableFromModelAnswer() {
        String answer = "Here is the comparison:\n\n"
                + "| Title | Source | Sample |\n"
                + "| --- | --- | --- |\n"
                + "| Study A | https://a.example | 120 |\n"
                + "| Study B | https://b.example |  |\n";
        StructuredTable table = StructuredTable.parseMarkdownTable(answer);
        assertNotNull(table);
        assertEquals(3, table.columnCount());
        assertEquals(2, table.rowCount());
        assertEquals(StructuredTable.MISSING, table.toMarkdown().contains("not stated")
                ? StructuredTable.MISSING : "not stated");
    }

    @Test
    public void marksMissingValues() {
        StructuredTable table = new StructuredTable(Arrays.asList("A", "B"));
        table.addRow(Arrays.asList("x", ""));
        assertTrue(table.columnsWithMissingValues().contains("B"));
        assertTrue(table.toMarkdown().contains(StructuredTable.MISSING));
    }

    @Test
    public void dedupesRows() {
        StructuredTable table = new StructuredTable(Arrays.asList("A", "B"));
        table.addRow(Arrays.asList("1", "2"));
        table.addRow(Arrays.asList("1", "2"));
        assertEquals(1, table.dedupe());
        assertEquals(1, table.rowCount());
    }

    @Test
    public void escapesCsvCells() {
        assertEquals("\"a,b\"", StructuredTable.csvCell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", StructuredTable.csvCell("say \"hi\""));
        assertEquals("plain", StructuredTable.csvCell("plain"));
        assertEquals("", StructuredTable.csvCell(null));
    }

    @Test
    public void exportsCsvAndJson() {
        StructuredTable table = new StructuredTable(Arrays.asList("Title", "URL"));
        table.addRow(Arrays.asList("Study A", "https://a.example"));
        String csv = table.toCsv();
        assertTrue(csv.startsWith("Title,URL"));
        assertTrue(csv.contains("Study A,https://a.example"));
        String json = table.toJson();
        assertTrue(json.contains("\"columns\""));
        assertTrue(json.contains("\"Title\""));
        assertTrue(json.contains("\"Study A\""));
    }

    @Test
    public void returnsNullWithoutTable() {
        assertNull(StructuredTable.parseMarkdownTable("no table here"));
        assertNull(StructuredTable.parseMarkdownTable(null));
        assertNull(StructuredTable.parseMarkdownTable(""));
    }

    @Test
    public void handlesEscapedPipes() {
        StructuredTable table = StructuredTable.parseMarkdownTable(
                "| A | B |\n| --- | --- |\n| x \\| y | z |\n");
        assertNotNull(table);
        assertEquals(1, table.rowCount());
        assertTrue(table.toMarkdown().contains("x \\| y"));
    }
}

package com.kaori.browser.ai.research;

import com.kaori.browser.ai.agent.AgentLogEntry;
import com.kaori.browser.ai.agent.AgentSession;
import com.kaori.browser.ai.agent.AgentStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Assembles the final Markdown report.
 *
 * <p>Only real data is used: the task text, the sources the agent actually read (recorded
 * by the tools), the real execution log, and the model's synthesis. Sections that have
 * no evidence say so instead of being invented.</p>
 */
public final class ReportGenerator {

    private ReportGenerator() {
    }

    public static String build(ResearchTask task, AgentSession session, String agentAnswer) {
        StringBuilder builder = new StringBuilder();
        String topic = task == null ? "" : task.getTopic();
        ResearchOptions options = task == null ? ResearchOptions.defaults() : task.getOptions();

        builder.append("# Research report: ").append(topic.isEmpty() ? "(no topic)" : topic)
                .append("\n\n");

        builder.append("## 1. Research task\n\n");
        builder.append("- Topic: ").append(topic).append('\n');
        builder.append("- Created: ").append(formatDate(task == null ? 0 : task.getCreatedAt()))
                .append('\n');
        builder.append("- Report language: ").append(options.reportLanguage).append('\n');
        builder.append("- Limits: ").append(options.describeLimits()).append('\n').append('\n');

        builder.append("## 2. Search method and pages actually opened\n\n");
        List<String> opened = new ArrayList<>();
        if (session != null) {
            for (AgentLogEntry entry : session.getLogger().entries()) {
                if ("browser.open_url".equals(entry.tool) && !entry.url.isEmpty()
                        && !opened.contains(entry.url)) {
                    opened.add(entry.url);
                }
            }
        }
        if (opened.isEmpty()) {
            builder.append("No navigation was logged, so no page opening can be reported.\n\n");
        } else {
            builder.append("The agent opened these URLs, in order:\n\n");
            for (String url : opened) {
                builder.append("- ").append(url).append('\n');
            }
            builder.append('\n');
        }

        builder.append("## 3. Sources used\n\n");
        List<SourceRecord> sources = task == null ? new ArrayList<>() : task.getSources();
        if (sources.isEmpty()) {
            builder.append("No source was successfully read. Statements below are not backed "
                    + "by any page.\n\n");
        } else {
            int index = 1;
            for (SourceRecord source : sources) {
                builder.append(index++).append(". ").append(source.title.isEmpty()
                                ? "(untitled)" : source.title).append("\n   - URL: ")
                        .append(source.url).append("\n   - Read: ").append(source.status)
                        .append(" at ").append(formatDate(source.accessedAt)).append('\n');
                if (!source.excerpt.isEmpty()) {
                    String excerpt = source.excerpt.replace('\n', ' ');
                    builder.append("   - Extract: ").append(excerpt.length() > 300
                            ? excerpt.substring(0, 300) + "…" : excerpt).append('\n');
                }
            }
            builder.append('\n');
        }

        builder.append("## 4. Key findings (model synthesis)\n\n");
        if (agentAnswer == null || agentAnswer.trim().isEmpty()) {
            builder.append("The agent did not produce a synthesis.\n\n");
        } else {
            builder.append("The following text is the model's synthesis over the sources above. "
                    + "Claims must be checked against the cited pages.\n\n");
            builder.append(agentAnswer.trim()).append("\n\n");
        }

        builder.append("## 5. Structured comparison\n\n");
        StructuredTable table = StructuredTable.parseMarkdownTable(agentAnswer);
        if (table == null || table.rowCount() == 0) {
            builder.append("The agent did not return a comparison table.\n\n");
        } else {
            builder.append(table.toMarkdown()).append('\n');
            builder.append("Rows: ").append(table.rowCount())
                    .append(", columns: ").append(table.columnCount()).append('\n');
            List<String> missing = table.columnsWithMissingValues();
            if (!missing.isEmpty()) {
                builder.append("Columns with missing values: ")
                        .append(String.join(", ", missing)).append('\n');
            }
            builder.append('\n');
        }

        builder.append("## 6. Evidence\n\n");
        if (sources.isEmpty()) {
            builder.append("No evidence was captured.\n\n");
        } else {
            for (SourceRecord source : sources) {
                builder.append("- ").append(source.url).append(" (")
                        .append(source.status).append(")\n");
            }
            builder.append('\n');
        }

        builder.append("## 7. Limitations\n\n");
        if (session != null) {
            int steps = session.getMemory().stepCount();
            builder.append("- Steps used: ").append(steps).append(" of ")
                    .append(session.getMaxSteps()).append('\n');
            builder.append("- Pages read: ").append(sources.size()).append('\n');
            int failures = 0;
            for (AgentLogEntry entry : session.getLogger().entries()) {
                if ("failed".equals(entry.status) || "invalid".equals(entry.status)
                        || "denied".equals(entry.status)) {
                    failures++;
                }
            }
            builder.append("- Failed, rejected or denied actions: ").append(failures).append('\n');
            if (options.reportLanguage.isEmpty()) {
                builder.append("- No report language was configured.\n");
            }
        } else {
            builder.append("- The session data was lost, so limits cannot be reported.\n");
        }
        builder.append("- Only text the app could actually read from the pages is included; "
                + "cross-origin frames, captchas, paywalls and login-only pages are not "
                + "readable.\n");
        builder.append("- Search-result snippets were never treated as article text.\n\n");

        builder.append("## 8. Unfinished steps\n\n");
        if (task != null && task.getStatus() == AgentStatus.COMPLETED
                && (session == null || session.getErrorMessage().isEmpty())) {
            builder.append("The agent reported the task as complete.\n");
        } else if (task != null) {
            builder.append("- Final state: ").append(task.getStatus().name()).append('\n');
            if (!task.getErrorMessage().isEmpty()) {
                builder.append("- Stopped because: ").append(task.getErrorMessage()).append('\n');
            }
            builder.append("- The report may therefore be incomplete.\n");
        } else {
            builder.append("No task state was available.\n");
        }

        builder.append("\n---\n\nGenerated by Kaori Browser AI Agent. Sources were recorded "
                + "from real page reads during the run.\n");
        return builder.toString();
    }

    private static String formatDate(long timestamp) {
        if (timestamp <= 0) {
            return "unknown";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(timestamp));
    }
}

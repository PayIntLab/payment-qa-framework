package io.pqa.framework;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Markdown / JSON report generation from scenario results. */
public final class ReportWriter {

    private ReportWriter() {}

    public static void writeMarkdown(List<ScenarioResult> results, Path path) throws Exception {
        long passed = results.stream().filter(ScenarioResult::passed).count();
        StringBuilder sb = new StringBuilder("# Payment QA Test Report\n\n");
        sb.append("> Total scenarios: ").append(results.size())
                .append("  Passed: ").append(passed)
                .append("  Failed: ").append(results.size() - passed).append("\n\n");
        sb.append("| Scenario | Result | Details |\n|---|---|---|\n");
        for (ScenarioResult r : results) {
            sb.append("| ").append(r.id()).append(" ").append(r.name())
                    .append(" | ").append(r.passed() ? "PASS" : "FAIL")
                    .append(" | ").append(r.detail().replace("|", "\\|")).append(" |\n");
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, sb.toString());
    }

    public static void writeJson(List<ScenarioResult> results, Path path) throws Exception {
        Files.createDirectories(path.getParent());
        ObjectMapper mapper = new ObjectMapper();
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }
}

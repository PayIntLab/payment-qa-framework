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
        StringBuilder sb = new StringBuilder("# 支付 QA 测试报告\n\n");
        sb.append("> 场景总数：").append(results.size())
                .append("　通过：").append(passed)
                .append("　失败：").append(results.size() - passed).append("\n\n");
        sb.append("| 场景 | 结果 | 说明 |\n|---|---|---|\n");
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

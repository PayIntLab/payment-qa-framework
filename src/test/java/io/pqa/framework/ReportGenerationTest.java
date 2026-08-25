package io.pqa.framework;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGenerationTest extends AbstractPaymentQaTest {

    @Test
    void generatesReportArtifacts() throws Exception {
        List<ScenarioResult> results = List.of(
                new ScenarioResult("A1", "First payment success", true, "charge=succeeded", 12),
                new ScenarioResult("D16", "Webhook idempotency", true, "fulfillments=1", 8),
                new ScenarioResult("E4", "Capture over authorization", true, "400 capture_exceeds_authorization", 15));

        Path markdown = Path.of("target", "payment-qa-report.md");
        Path json = Path.of("target", "payment-qa-report.json");
        ReportWriter.writeMarkdown(results, markdown);
        ReportWriter.writeJson(results, json);

        String content = Files.readString(markdown);
        assertTrue(content.contains("PASS"));
        assertTrue(content.contains("Total scenarios: 3"));
        assertTrue(Files.exists(json));
    }
}

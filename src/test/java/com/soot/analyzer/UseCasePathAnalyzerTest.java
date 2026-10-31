package com.soot.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.soot.analyzer.model.AnalysisResult;
import com.soot.analyzer.model.EntrypointAnalysis;
import com.soot.analyzer.model.EntrypointType;
import com.soot.analyzer.model.PathType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class UseCasePathAnalyzerTest {

    private static final String TEST_CLASSPATH = "target/test-classes";
    private static final String BASE_PACKAGE = "com.bforbank";

    @Test
    @DisplayName("Should analyze TransferController entrypoint and extract execution paths and sinks (legacy signature)")
    void testTransferControllerAnalysis() throws Exception {
        File cpFile = new File(TEST_CLASSPATH);
        assertTrue(cpFile.exists(), "target/test-classes must exist before running test");

        String entrypoint = "<com.bforbank.sample.controller.TransferController: void executeTransfer(com.bforbank.sample.controller.TransferController$TransferDto)>";

        AnalysisResult result = UseCasePathAnalyzer.runAnalysis(TEST_CLASSPATH, entrypoint, BASE_PACKAGE, false);

        assertNotNull(result);
        assertEquals("com.bforbank.sample.controller.TransferController", result.getEntrypointClass());
        assertEquals("executeTransfer", result.getEntrypointMethod());
        assertEquals(BASE_PACKAGE, result.getBasePackage());

        // Verify detected paths and rejections
        assertTrue(result.getTotalPaths() > 0, "Should detect at least 1 path");
        assertTrue(result.getRejectionPathsCount() >= 1, "Should detect rejection/guard paths");
        assertFalse(result.getDetectedSinks().isEmpty(), "Should detect hexagonal out port / sinks");

        // Verify that InsufficientFundsException rejection path is detected
        boolean foundInsufficientFunds = result.getPaths().stream()
                .filter(p -> p.getType() == PathType.REJECTION)
                .anyMatch(p -> p.getTerminalRejection() != null &&
                        p.getTerminalRejection().getExceptionClass().contains("InsufficientFundsException"));
        assertTrue(foundInsufficientFunds, "Should detect InsufficientFundsException rejection path");
    }

    @Test
    @DisplayName("Should automatically detect all entrypoints and run path analysis without CLI entrypoint argument")
    void testAutomatedMultiEntrypointAnalysis() throws Exception {
        AnalysisResult result = UseCasePathAnalyzer.runAnalysis(TEST_CLASSPATH, null, BASE_PACKAGE, null, false);

        assertNotNull(result);
        assertEquals(BASE_PACKAGE, result.getBasePackage());
        assertTrue(result.getTotalEntrypoints() >= 5, "Should auto-detect multiple entrypoints (REST, Kafka, Scheduled, Batch, Runner...)");
        assertTrue(result.getTotalPaths() > 0, "Should discover paths across all detected entrypoints");
        assertTrue(result.getTotalSinks() > 0, "Should discover sinks across entrypoints");

        // Verify JSON serialization of consolidated result
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(result);
        assertNotNull(json);
        assertTrue(json.contains("entrypoints"));
        assertTrue(json.contains("REST_ENDPOINT"));
        assertTrue(json.contains("KAFKA_LISTENER"));

        // Verify REST entrypoint details
        EntrypointAnalysis restAnalysis = result.getEntrypoints().stream()
                .filter(e -> e.getType() == EntrypointType.REST_ENDPOINT)
                .findFirst()
                .orElse(null);
        assertNotNull(restAnalysis);
        assertEquals("POST", restAnalysis.getMetadata().get("httpMethod"));
        assertTrue(restAnalysis.getTotalPaths() >= 1);

        // Verify Kafka entrypoint details
        EntrypointAnalysis kafkaAnalysis = result.getEntrypoints().stream()
                .filter(e -> e.getType() == EntrypointType.KAFKA_LISTENER)
                .findFirst()
                .orElse(null);
        assertNotNull(kafkaAnalysis);
        assertEquals("account-transfers", kafkaAnalysis.getMetadata().get("topics"));
    }

    @Test
    @DisplayName("Should filter entrypoints by type when typeFilter is provided")
    void testTypeFilteringAnalysis() throws Exception {
        AnalysisResult result = UseCasePathAnalyzer.runAnalysis(TEST_CLASSPATH, null, BASE_PACKAGE, "kafka", false);

        assertNotNull(result);
        assertEquals(1, result.getTotalEntrypoints(), "Should only analyze Kafka listener entrypoints");
        assertEquals(EntrypointType.KAFKA_LISTENER, result.getEntrypoints().get(0).getType());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when no entrypoints are detected in given package")
    void testNoEntrypointsFoundThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            UseCasePathAnalyzer.runAnalysis(TEST_CLASSPATH, null, "com.nonexistent.package", null, false);
        });
    }
}

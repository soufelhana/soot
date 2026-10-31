package com.soot.analyzer;

import com.soot.analyzer.model.EntrypointInfo;
import com.soot.analyzer.model.EntrypointType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soot.G;
import soot.Scene;
import soot.options.Options;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntrypointDetectorTest {

    @BeforeAll
    static void initSoot() {
        G.reset();
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_prepend_classpath(true);

        String testClasspath = "target/test-classes";
        File cpFile = new File(testClasspath);
        assertTrue(cpFile.exists(), "target/test-classes must exist");

        Options.v().set_process_dir(Collections.singletonList(cpFile.getAbsolutePath()));
        Options.v().set_soot_classpath(cpFile.getAbsolutePath());

        Scene.v().loadNecessaryClasses();
    }

    @Test
    @DisplayName("Should detect all application entrypoints across REST, Kafka, Scheduled, Batch, Runner, and Temporal")
    void testDetectEntrypoints() {
        List<EntrypointInfo> entrypoints = EntrypointDetector.detectEntrypoints(
                Scene.v().getApplicationClasses(), "com.bforbank.sample"
        );

        assertNotNull(entrypoints);
        assertFalse(entrypoints.isEmpty(), "Should detect entrypoints in com.bforbank.sample");

        // 1. Verify REST endpoint
        EntrypointInfo restEp = entrypoints.stream()
                .filter(e -> e.getType() == EntrypointType.REST_ENDPOINT)
                .findFirst()
                .orElse(null);
        assertNotNull(restEp, "REST endpoint should be detected");
        assertTrue(restEp.getClassName().contains("TransferController"));
        assertEquals("POST", restEp.getMetadata().get("httpMethod"));
        assertEquals("/api/v1/transfers", restEp.getMetadata().get("path"));

        // 2. Verify Kafka listener
        EntrypointInfo kafkaEp = entrypoints.stream()
                .filter(e -> e.getType() == EntrypointType.KAFKA_LISTENER)
                .findFirst()
                .orElse(null);
        assertNotNull(kafkaEp, "Kafka listener should be detected");
        assertTrue(kafkaEp.getClassName().contains("AccountEventListener"));
        assertEquals("account-transfers", kafkaEp.getMetadata().get("topics"));
        assertEquals("transfer-group", kafkaEp.getMetadata().get("groupId"));

        // 3. Verify Scheduled task
        EntrypointInfo schedEp = entrypoints.stream()
                .filter(e -> e.getType() == EntrypointType.SCHEDULED_TASK)
                .findFirst()
                .orElse(null);
        assertNotNull(schedEp, "Scheduled task should be detected");
        assertTrue(schedEp.getClassName().contains("TransferCleanupScheduler"));
        assertEquals("0 0 2 * * ?", schedEp.getMetadata().get("cron"));

        // 4. Verify Batch Tasklet
        EntrypointInfo batchEp = entrypoints.stream()
                .filter(e -> e.getType() == EntrypointType.BATCH_JOB)
                .findFirst()
                .orElse(null);
        assertNotNull(batchEp, "Spring Batch Tasklet should be detected");
        assertTrue(batchEp.getClassName().contains("TransferArchiveTasklet"));
        assertEquals("SpringBatch", batchEp.getMetadata().get("engine"));

        // 5. Verify Application Runner
        EntrypointInfo runnerEp = entrypoints.stream()
                .filter(e -> e.getType() == EntrypointType.APPLICATION_RUNNER)
                .findFirst()
                .orElse(null);
        assertNotNull(runnerEp, "CommandLineRunner should be detected");
        assertTrue(runnerEp.getClassName().contains("TransferStartupRunner"));

        // 6. Verify Temporal Workflow
        EntrypointInfo workflowEp = entrypoints.stream()
                .filter(e -> e.getType() == EntrypointType.TEMPORAL_WORKFLOW)
                .findFirst()
                .orElse(null);
        assertNotNull(workflowEp, "Temporal workflow should be detected");
        assertTrue(workflowEp.getClassName().contains("TransferWorkflow"));
    }
}

package com.soot.analyzer.model;

/**
 * Enumeration of supported application entrypoint (source node) types.
 */
public enum EntrypointType {
    REST_ENDPOINT,
    KAFKA_LISTENER,
    MESSAGING_LISTENER,
    SCHEDULED_TASK,
    BATCH_JOB,
    SPRING_EVENT,
    APPLICATION_RUNNER,
    TEMPORAL_WORKFLOW,
    TEMPORAL_ACTIVITY,
    MANUAL_ENTRYPOINT
}

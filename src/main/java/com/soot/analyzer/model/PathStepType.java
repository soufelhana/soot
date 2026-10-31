package com.soot.analyzer.model;

public enum PathStepType {
    ENTRYPOINT,
    METHOD_CALL,
    LAMBDA_CALL,
    BRANCH_DECISION,
    GUARD_VALIDATION,
    THROW_EXCEPTION,
    SINK_DATABASE,
    SINK_KAFKA,
    SINK_PORT,
    RETURN
}

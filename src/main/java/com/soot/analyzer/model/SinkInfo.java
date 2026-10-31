package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SinkInfo {
    private SinkType type;
    private String targetClass;
    private String targetMethod;
    private String signature;
    private int sourceLine;
    private String description;

    public SinkInfo() {}

    public SinkInfo(SinkType type, String targetClass, String targetMethod, String signature, int sourceLine, String description) {
        this.type = type;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.signature = signature;
        this.sourceLine = sourceLine;
        this.description = description;
    }

    public SinkType getType() { return type; }
    public void setType(SinkType type) { this.type = type; }

    public String getTargetClass() { return targetClass; }
    public void setTargetClass(String targetClass) { this.targetClass = targetClass; }

    public String getTargetMethod() { return targetMethod; }
    public void setTargetMethod(String targetMethod) { this.targetMethod = targetMethod; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int sourceLine) { this.sourceLine = sourceLine; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

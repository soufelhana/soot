package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathStep {
    private int stepNumber;
    private String className;
    private String methodName;
    private String signature;
    private int sourceLine;
    private PathStepType type;
    private String instruction;
    private String detail;
    private SinkInfo sink;
    private RejectionInfo rejection;

    public PathStep() {}

    public PathStep(int stepNumber, String className, String methodName, String signature, int sourceLine,
                    PathStepType type, String instruction, String detail) {
        this.stepNumber = stepNumber;
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.sourceLine = sourceLine;
        this.type = type;
        this.instruction = instruction;
        this.detail = detail;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int sourceLine) { this.sourceLine = sourceLine; }

    public PathStepType getType() { return type; }
    public void setType(PathStepType type) { this.type = type; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public SinkInfo getSink() { return sink; }
    public void setSink(SinkInfo sink) { this.sink = sink; }

    public RejectionInfo getRejection() { return rejection; }
    public void setRejection(RejectionInfo rejection) { this.rejection = rejection; }

    @Override
    public String toString() {
        return className + "." + methodName + "():" + sourceLine + " [" + type + "] " + detail;
    }
}

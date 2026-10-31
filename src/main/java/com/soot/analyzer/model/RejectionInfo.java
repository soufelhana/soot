package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RejectionInfo {
    private String exceptionClass;
    private String enclosingClass;
    private String enclosingMethod;
    private int sourceLine;
    private String instruction;
    private String description;

    public RejectionInfo() {}

    public RejectionInfo(String exceptionClass, String enclosingClass, String enclosingMethod, int sourceLine, String instruction, String description) {
        this.exceptionClass = exceptionClass;
        this.enclosingClass = enclosingClass;
        this.enclosingMethod = enclosingMethod;
        this.sourceLine = sourceLine;
        this.instruction = instruction;
        this.description = description;
    }

    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }

    public String getEnclosingClass() { return enclosingClass; }
    public void setEnclosingClass(String enclosingClass) { this.enclosingClass = enclosingClass; }

    public String getEnclosingMethod() { return enclosingMethod; }
    public void setEnclosingMethod(String enclosingMethod) { this.enclosingMethod = enclosingMethod; }

    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int sourceLine) { this.sourceLine = sourceLine; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

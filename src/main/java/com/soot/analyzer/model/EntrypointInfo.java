package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Information describing a detected application entrypoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntrypointInfo {
    private EntrypointType type;
    private String className;
    private String methodName;
    private String signature;
    private int sourceLine;
    private String description;
    private Map<String, String> metadata = new LinkedHashMap<>();

    public EntrypointInfo() {}

    public EntrypointInfo(EntrypointType type, String className, String methodName, String signature, int sourceLine, String description) {
        this.type = type;
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.sourceLine = sourceLine;
        this.description = description;
    }

    public EntrypointType getType() { return type; }
    public void setType(EntrypointType type) { this.type = type; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int sourceLine) { this.sourceLine = sourceLine; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public void addMetadata(String key, String value) {
        if (key != null && value != null) {
            this.metadata.put(key, value);
        }
    }
}

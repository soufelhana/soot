package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;

/**
 * Detailed path and sink analysis for an individual entrypoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntrypointAnalysis {
    private EntrypointInfo entrypointInfo;
    private String entrypoint;
    private String entrypointClass;
    private String entrypointMethod;
    private int entrypointLine;
    private EntrypointType type;
    private String description;
    private Map<String, String> metadata = new LinkedHashMap<>();
    private String basePackage;
    private int totalPaths;
    private int nominalPathsCount;
    private int rejectionPathsCount;
    private List<SinkInfo> detectedSinks = new ArrayList<>();
    private List<RejectionInfo> detectedRejections = new ArrayList<>();
    private List<ExecutionPath> paths = new ArrayList<>();
    private Map<String, List<Integer>> sourceLinesByClass = new TreeMap<>();

    public EntrypointAnalysis() {}

    public EntrypointAnalysis(EntrypointInfo info) {
        if (info != null) {
            this.entrypointInfo = info;
            this.entrypoint = info.getSignature();
            this.entrypointClass = info.getClassName();
            this.entrypointMethod = info.getMethodName();
            this.entrypointLine = info.getSourceLine();
            this.type = info.getType();
            this.description = info.getDescription();
            if (info.getMetadata() != null) {
                this.metadata.putAll(info.getMetadata());
            }
        }
    }

    public EntrypointInfo getEntrypointInfo() { return entrypointInfo; }
    public void setEntrypointInfo(EntrypointInfo entrypointInfo) { this.entrypointInfo = entrypointInfo; }

    public String getEntrypoint() { return entrypoint; }
    public void setEntrypoint(String entrypoint) { this.entrypoint = entrypoint; }

    public String getEntrypointClass() { return entrypointClass; }
    public void setEntrypointClass(String entrypointClass) { this.entrypointClass = entrypointClass; }

    public String getEntrypointMethod() { return entrypointMethod; }
    public void setEntrypointMethod(String entrypointMethod) { this.entrypointMethod = entrypointMethod; }

    public int getEntrypointLine() { return entrypointLine; }
    public void setEntrypointLine(int entrypointLine) { this.entrypointLine = entrypointLine; }

    public EntrypointType getType() { return type; }
    public void setType(EntrypointType type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public int getTotalPaths() { return totalPaths; }
    public void setTotalPaths(int totalPaths) { this.totalPaths = totalPaths; }

    public int getNominalPathsCount() { return nominalPathsCount; }
    public void setNominalPathsCount(int nominalPathsCount) { this.nominalPathsCount = nominalPathsCount; }

    public int getRejectionPathsCount() { return rejectionPathsCount; }
    public void setRejectionPathsCount(int rejectionPathsCount) { this.rejectionPathsCount = rejectionPathsCount; }

    public List<SinkInfo> getDetectedSinks() { return detectedSinks; }
    public void setDetectedSinks(List<SinkInfo> detectedSinks) { this.detectedSinks = detectedSinks; }

    public List<RejectionInfo> getDetectedRejections() { return detectedRejections; }
    public void setDetectedRejections(List<RejectionInfo> detectedRejections) { this.detectedRejections = detectedRejections; }

    public List<ExecutionPath> getPaths() { return paths; }
    public void setPaths(List<ExecutionPath> paths) { this.paths = paths; }

    public Map<String, List<Integer>> getSourceLinesByClass() { return sourceLinesByClass; }
    public void setSourceLinesByClass(Map<String, List<Integer>> sourceLinesByClass) { this.sourceLinesByClass = sourceLinesByClass; }

    public void addSourceLine(String className, int line) {
        if (className == null || line <= 0) return;
        sourceLinesByClass.computeIfAbsent(className, k -> new ArrayList<>());
        if (!sourceLinesByClass.get(className).contains(line)) {
            sourceLinesByClass.get(className).add(line);
            Collections.sort(sourceLinesByClass.get(className));
        }
    }
}

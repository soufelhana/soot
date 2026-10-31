package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;

/**
 * Root consolidated analysis result holding global summary and individual entrypoint analyses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResult {
    private String analyzerVersion = "1.0.0 (Soot 4.5.0)";
    private String analysisTimestamp;
    private String basePackage;
    private int totalEntrypoints;
    private int totalPaths;
    private int nominalPathsCount;
    private int rejectionPathsCount;
    private int totalSinks;
    private List<EntrypointAnalysis> entrypoints = new ArrayList<>();

    // Backward compatibility fields (populated if single entrypoint or accessed by legacy code)
    private String entrypoint;
    private String entrypointClass;
    private String entrypointMethod;
    private int entrypointLine;
    private List<SinkInfo> detectedSinks;
    private List<RejectionInfo> detectedRejections;
    private List<ExecutionPath> paths;
    private Map<String, List<Integer>> sourceLinesByClass;

    public AnalysisResult() {}

    public String getAnalyzerVersion() { return analyzerVersion; }
    public void setAnalyzerVersion(String analyzerVersion) { this.analyzerVersion = analyzerVersion; }

    public String getAnalysisTimestamp() { return analysisTimestamp; }
    public void setAnalysisTimestamp(String analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public int getTotalEntrypoints() { return totalEntrypoints; }
    public void setTotalEntrypoints(int totalEntrypoints) { this.totalEntrypoints = totalEntrypoints; }

    public int getTotalPaths() { return totalPaths; }
    public void setTotalPaths(int totalPaths) { this.totalPaths = totalPaths; }

    public int getNominalPathsCount() { return nominalPathsCount; }
    public void setNominalPathsCount(int nominalPathsCount) { this.nominalPathsCount = nominalPathsCount; }

    public int getRejectionPathsCount() { return rejectionPathsCount; }
    public void setRejectionPathsCount(int rejectionPathsCount) { this.rejectionPathsCount = rejectionPathsCount; }

    public int getTotalSinks() { return totalSinks; }
    public void setTotalSinks(int totalSinks) { this.totalSinks = totalSinks; }

    public List<EntrypointAnalysis> getEntrypoints() { return entrypoints; }
    public void setEntrypoints(List<EntrypointAnalysis> entrypoints) {
        this.entrypoints = entrypoints;
        recalculateTotals();
    }

    public void addEntrypointAnalysis(EntrypointAnalysis ea) {
        if (ea != null) {
            this.entrypoints.add(ea);
            recalculateTotals();
        }
    }

    public void recalculateTotals() {
        this.totalEntrypoints = entrypoints.size();
        int pathsSum = 0;
        int nominalSum = 0;
        int rejectionSum = 0;
        int sinksSum = 0;

        for (EntrypointAnalysis ea : entrypoints) {
            pathsSum += ea.getTotalPaths();
            nominalSum += ea.getNominalPathsCount();
            rejectionSum += ea.getRejectionPathsCount();
            sinksSum += ea.getDetectedSinks().size();
        }

        this.totalPaths = pathsSum;
        this.nominalPathsCount = nominalSum;
        this.rejectionPathsCount = rejectionSum;
        this.totalSinks = sinksSum;

        if (!entrypoints.isEmpty()) {
            EntrypointAnalysis first = entrypoints.get(0);
            this.entrypoint = first.getEntrypoint();
            this.entrypointClass = first.getEntrypointClass();
            this.entrypointMethod = first.getEntrypointMethod();
            this.entrypointLine = first.getEntrypointLine();
            this.paths = first.getPaths();
            this.detectedSinks = first.getDetectedSinks();
            this.detectedRejections = first.getDetectedRejections();
            this.sourceLinesByClass = first.getSourceLinesByClass();
        }
    }

    // --- Backward compatibility getters & setters ---

    public String getEntrypoint() {
        return entrypoint != null ? entrypoint : (entrypoints.isEmpty() ? null : entrypoints.get(0).getEntrypoint());
    }
    public void setEntrypoint(String entrypoint) { this.entrypoint = entrypoint; }

    public String getEntrypointClass() {
        return entrypointClass != null ? entrypointClass : (entrypoints.isEmpty() ? null : entrypoints.get(0).getEntrypointClass());
    }
    public void setEntrypointClass(String entrypointClass) { this.entrypointClass = entrypointClass; }

    public String getEntrypointMethod() {
        return entrypointMethod != null ? entrypointMethod : (entrypoints.isEmpty() ? null : entrypoints.get(0).getEntrypointMethod());
    }
    public void setEntrypointMethod(String entrypointMethod) { this.entrypointMethod = entrypointMethod; }

    public int getEntrypointLine() {
        return entrypointLine != 0 ? entrypointLine : (entrypoints.isEmpty() ? 0 : entrypoints.get(0).getEntrypointLine());
    }
    public void setEntrypointLine(int entrypointLine) { this.entrypointLine = entrypointLine; }

    public List<ExecutionPath> getPaths() {
        return paths != null ? paths : (entrypoints.isEmpty() ? Collections.emptyList() : entrypoints.get(0).getPaths());
    }
    public void setPaths(List<ExecutionPath> paths) { this.paths = paths; }

    public List<SinkInfo> getDetectedSinks() {
        return detectedSinks != null ? detectedSinks : (entrypoints.isEmpty() ? Collections.emptyList() : entrypoints.get(0).getDetectedSinks());
    }
    public void setDetectedSinks(List<SinkInfo> detectedSinks) { this.detectedSinks = detectedSinks; }

    public List<RejectionInfo> getDetectedRejections() {
        return detectedRejections != null ? detectedRejections : (entrypoints.isEmpty() ? Collections.emptyList() : entrypoints.get(0).getDetectedRejections());
    }
    public void setDetectedRejections(List<RejectionInfo> detectedRejections) { this.detectedRejections = detectedRejections; }

    public Map<String, List<Integer>> getSourceLinesByClass() {
        return sourceLinesByClass != null ? sourceLinesByClass : (entrypoints.isEmpty() ? Collections.emptyMap() : entrypoints.get(0).getSourceLinesByClass());
    }
    public void setSourceLinesByClass(Map<String, List<Integer>> sourceLinesByClass) { this.sourceLinesByClass = sourceLinesByClass; }

    public void addSourceLine(String className, int line) {
        if (!entrypoints.isEmpty()) {
            entrypoints.get(0).addSourceLine(className, line);
        }
    }
}

package com.soot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionPath {
    private String pathId;
    private PathType type;
    private String description;
    private String terminalReason;
    private SinkInfo terminalSink;
    private RejectionInfo terminalRejection;
    private List<PathStep> steps = new ArrayList<>();

    public ExecutionPath() {}

    public ExecutionPath(String pathId, PathType type, String description, String terminalReason) {
        this.pathId = pathId;
        this.type = type;
        this.description = description;
        this.terminalReason = terminalReason;
    }

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) { this.pathId = pathId; }

    public PathType getType() { return type; }
    public void setType(PathType type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTerminalReason() { return terminalReason; }
    public void setTerminalReason(String terminalReason) { this.terminalReason = terminalReason; }

    public SinkInfo getTerminalSink() { return terminalSink; }
    public void setTerminalSink(SinkInfo terminalSink) { this.terminalSink = terminalSink; }

    public RejectionInfo getTerminalRejection() { return terminalRejection; }
    public void setTerminalRejection(RejectionInfo terminalRejection) { this.terminalRejection = terminalRejection; }

    public List<PathStep> getSteps() { return steps; }
    public void setSteps(List<PathStep> steps) { this.steps = steps; }

    public void addStep(PathStep step) {
        this.steps.add(step);
    }
}

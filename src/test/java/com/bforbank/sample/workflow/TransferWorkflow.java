package com.bforbank.sample.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TransferWorkflow {

    @WorkflowMethod(name = "ExecuteTransferWorkflow")
    void executeWorkflow(String sourceId, String targetId, double amount);
}

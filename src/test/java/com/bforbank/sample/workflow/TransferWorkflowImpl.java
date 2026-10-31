package com.bforbank.sample.workflow;

import com.bforbank.sample.ports.out.AccountRepositoryPort;

public class TransferWorkflowImpl implements TransferWorkflow {

    private final AccountRepositoryPort repositoryPort;

    public TransferWorkflowImpl(AccountRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public void executeWorkflow(String sourceId, String targetId, double amount) {
        repositoryPort.findById(sourceId);
    }
}

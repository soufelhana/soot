package com.bforbank.sample.batch;

import com.bforbank.sample.ports.out.AccountRepositoryPort;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class TransferArchiveTasklet implements Tasklet {

    private final AccountRepositoryPort repositoryPort;

    public TransferArchiveTasklet(AccountRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        repositoryPort.findById("ARCHIVE_JOB");
        return RepeatStatus.FINISHED;
    }
}

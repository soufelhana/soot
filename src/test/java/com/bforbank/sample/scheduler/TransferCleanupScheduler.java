package com.bforbank.sample.scheduler;

import com.bforbank.sample.ports.out.AccountRepositoryPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransferCleanupScheduler {

    private final AccountRepositoryPort repositoryPort;

    public TransferCleanupScheduler(AccountRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void runNightlyCleanup() {
        repositoryPort.findById("SYSTEM_CLEANUP");
    }
}

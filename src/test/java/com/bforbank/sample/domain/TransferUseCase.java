package com.bforbank.sample.domain;

import com.bforbank.sample.ports.out.AccountRepositoryPort;
import com.bforbank.sample.ports.out.TransferEventPort;

public class TransferUseCase {
    private final AccountRepositoryPort accountRepository;
    private final TransferEventPort eventPort;

    public TransferUseCase(AccountRepositoryPort accountRepository, TransferEventPort eventPort) {
        this.accountRepository = accountRepository;
        this.eventPort = eventPort;
    }

    public void executeTransfer(String sourceId, String targetId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive");
        }

        Account source = accountRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + sourceId));

        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target account not found: " + targetId));

        // Business logic & invariant check inside domain aggregate
        source.withdraw(amount);
        target.deposit(amount);

        // Database Sinks
        accountRepository.save(source);
        accountRepository.save(target);

        // Messaging Sink (Kafka)
        eventPort.emitTransferCompleted(sourceId, targetId, amount);
    }
}

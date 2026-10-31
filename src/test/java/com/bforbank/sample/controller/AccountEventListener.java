package com.bforbank.sample.controller;

import com.bforbank.sample.domain.TransferUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountEventListener {

    private final TransferUseCase transferUseCase;

    public AccountEventListener(TransferUseCase transferUseCase) {
        this.transferUseCase = transferUseCase;
    }

    @KafkaListener(topics = "account-transfers", groupId = "transfer-group")
    public void onTransferEvent(TransferEventPayload event) {
        transferUseCase.executeTransfer(event.fromAccount(), event.toAccount(), event.transferAmount());
    }

    public record TransferEventPayload(String fromAccount, String toAccount, double transferAmount) {}
}

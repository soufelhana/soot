package com.bforbank.sample.controller;

import com.bforbank.sample.domain.TransferUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferUseCase transferUseCase;

    public TransferController(TransferUseCase transferUseCase) {
        this.transferUseCase = transferUseCase;
    }

    @PostMapping
    public void executeTransfer(@RequestBody TransferDto request) {
        transferUseCase.executeTransfer(request.sourceId(), request.targetId(), request.amount());
    }

    public record TransferDto(String sourceId, String targetId, double amount) {}
}

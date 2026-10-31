package com.bforbank.sample.ports.out;

public interface TransferEventPort {
    void emitTransferCompleted(String sourceId, String destId, double amount);
}

package com.bforbank.sample.runner;

import com.bforbank.sample.ports.out.AccountRepositoryPort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TransferStartupRunner implements CommandLineRunner {

    private final AccountRepositoryPort repositoryPort;

    public TransferStartupRunner(AccountRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public void run(String... args) throws Exception {
        repositoryPort.findById("INIT_CHECK");
    }
}

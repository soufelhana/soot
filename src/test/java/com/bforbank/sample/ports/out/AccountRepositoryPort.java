package com.bforbank.sample.ports.out;

import com.bforbank.sample.domain.Account;
import java.util.Optional;

public interface AccountRepositoryPort {
    Optional<Account> findById(String id);
    void save(Account account);
}

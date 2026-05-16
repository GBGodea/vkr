package com.example.bank.repository;

import com.example.bank.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account create(String ownerName, String currency, BigDecimal initialBalance, Instant createdAt);

    Account save(Account account);

    Optional<Account> findById(Long id);

    List<Account> findAll();
}

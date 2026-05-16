package com.example.bank.repository;

import com.example.bank.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAccountRepository implements AccountRepository {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<Long, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Account create(String ownerName, String currency, BigDecimal initialBalance, Instant createdAt) {
        Account account = new Account(sequence.incrementAndGet(), ownerName, currency, initialBalance, createdAt);
        accounts.put(account.getId(), account);
        return account;
    }

    @Override
    public Account save(Account account) {
        accounts.put(account.getId(), account);
        return account;
    }

    @Override
    public Optional<Account> findById(Long id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public List<Account> findAll() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(Account::getId))
                .toList();
    }
}

package com.example.bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bank.domain.AccountStatus;
import com.example.bank.domain.TransferStatus;
import com.example.bank.dto.AccountCreateRequest;
import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.BlockAccountRequest;
import com.example.bank.dto.MoneyOperationRequest;
import com.example.bank.dto.TransferRequest;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.InMemoryAccountRepository;
import com.example.bank.repository.InMemoryTransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankServiceTest {

    private BankService bankService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        bankService = new BankService(
                new InMemoryAccountRepository(),
                new InMemoryTransferRepository(),
                fixedClock
        );
    }

    @Test
    void createAccountCreatesActiveAccountWithNormalizedData() {
        AccountResponse account = bankService.createAccount(new AccountCreateRequest(
                "  Ivan Petrov  ",
                "rub",
                new BigDecimal("1000.00")
        ));

        assertThat(account.id()).isEqualTo(1L);
        assertThat(account.ownerName()).isEqualTo("Ivan Petrov");
        assertThat(account.currency()).isEqualTo("RUB");
        assertThat(account.balance()).isEqualByComparingTo("1000.00");
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void depositIncreasesBalanceOfActiveAccount() {
        AccountResponse account = createAccount("Ivan Petrov", "RUB", "1000.00");

        AccountResponse updated = bankService.deposit(
                account.id(),
                new MoneyOperationRequest(new BigDecimal("250.50"))
        );

        assertThat(updated.balance()).isEqualByComparingTo("1250.50");
    }

    @Test
    void withdrawDecreasesBalanceWhenFundsAreEnough() {
        AccountResponse account = createAccount("Ivan Petrov", "RUB", "1000.00");

        AccountResponse updated = bankService.withdraw(
                account.id(),
                new MoneyOperationRequest(new BigDecimal("125.25"))
        );

        assertThat(updated.balance()).isEqualByComparingTo("874.75");
    }

    @Test
    void transferMovesMoneyBetweenActiveAccountsAndPreservesTotalBalance() {
        AccountResponse source = createAccount("Ivan Petrov", "RUB", "1000.00");
        AccountResponse target = createAccount("Petr Ivanov", "RUB", "100.00");

        var transfer = bankService.transfer(new TransferRequest(
                source.id(),
                target.id(),
                new BigDecimal("300.00")
        ));

        AccountResponse sourceAfter = bankService.getAccount(source.id());
        AccountResponse targetAfter = bankService.getAccount(target.id());

        assertThat(transfer.id()).isNotNull();
        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.currency()).isEqualTo("RUB");
        assertThat(sourceAfter.balance()).isEqualByComparingTo("700.00");
        assertThat(targetAfter.balance()).isEqualByComparingTo("400.00");
        assertThat(sourceAfter.balance().add(targetAfter.balance())).isEqualByComparingTo("1100.00");
    }

    @Test
    void blockAccountChangesStatusAndPreventsMoneyOperations() {
        AccountResponse account = createAccount("Ivan Petrov", "RUB", "1000.00");

        AccountResponse blocked = bankService.blockAccount(account.id(), new BlockAccountRequest("Suspicious activity"));

        assertThat(blocked.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(blocked.blockReason()).isEqualTo("Suspicious activity");
        assertThat(blocked.blockedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));

        assertThatThrownBy(() -> bankService.withdraw(
                account.id(),
                new MoneyOperationRequest(new BigDecimal("10.00"))
        ))
                .isInstanceOf(BankException.class)
                .satisfies(error -> assertThat(((BankException) error).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_BLOCKED));
    }

    @Test
    void withdrawRejectsInsufficientFunds() {
        AccountResponse account = createAccount("Ivan Petrov", "RUB", "100.00");

        assertThatThrownBy(() -> bankService.withdraw(
                account.id(),
                new MoneyOperationRequest(new BigDecimal("150.00"))
        ))
                .isInstanceOf(BankException.class)
                .satisfies(error -> assertThat(((BankException) error).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        assertThat(bankService.getAccount(account.id()).balance()).isEqualByComparingTo("100.00");
    }

    private AccountResponse createAccount(String ownerName, String currency, String initialBalance) {
        return bankService.createAccount(new AccountCreateRequest(
                ownerName,
                currency,
                new BigDecimal(initialBalance)
        ));
    }
}

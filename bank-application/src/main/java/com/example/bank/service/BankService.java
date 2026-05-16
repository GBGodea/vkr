package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountStatus;
import com.example.bank.domain.Transfer;
import com.example.bank.domain.TransferStatus;
import com.example.bank.dto.AccountCreateRequest;
import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.BlockAccountRequest;
import com.example.bank.dto.MoneyOperationRequest;
import com.example.bank.dto.TransferRequest;
import com.example.bank.dto.TransferResponse;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.vkr.verification.BigDecimalSpec;
import ru.vkr.verification.Contract;
import ru.vkr.verification.annotation.Invariant;
import ru.vkr.verification.annotation.Postcondition;
import ru.vkr.verification.annotation.Precondition;
import ru.vkr.verification.annotation.Verified;

@Service
@Verified(id = "BANK-SERVICE", description = "Critical banking operations with executable contracts")
@Invariant("account balances are non-negative")
@Invariant("completed transfers preserve the total balance of source and target accounts")
public class BankService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final Clock clock;

    public BankService(AccountRepository accountRepository, TransferRepository transferRepository, Clock clock) {
        this.accountRepository = Contract.requiresNonNull(accountRepository, "account repository must be defined");
        this.transferRepository = Contract.requiresNonNull(transferRepository, "transfer repository must be defined");
        this.clock = Contract.requiresNonNull(clock, "clock must be defined");
    }

    @Verified(id = "BANK-CREATE-ACCOUNT", description = "Create active account with normalized data")
    @Precondition("request contains valid owner, currency and non-negative initial balance")
    @Postcondition("created account is ACTIVE and has non-negative balance")
    public synchronized AccountResponse createAccount(AccountCreateRequest request) {
        Contract.requires(request != null, "createAccount request must be defined");
        String ownerName = normalizeOwnerName(request.ownerName());
        String currency = normalizeCurrency(request.currency());
        BigDecimal initialBalance = MoneyRules.requireNonNegative(request.initialBalance());

        Account account = accountRepository.create(ownerName, currency, initialBalance, Instant.now(clock));
        verifyAccountInvariant(account);
        Contract.ensures(account.getStatus() == AccountStatus.ACTIVE, "created account must be active");
        Contract.ensures(BigDecimalSpec.isEqualByValue(account.getBalance(), initialBalance),
                "created account balance must equal initial balance");
        return AccountResponse.from(account);
    }

    public AccountResponse getAccount(Long accountId) {
        return AccountResponse.from(requireAccount(accountId));
    }

    public List<AccountResponse> getAccounts() {
        return accountRepository.findAll().stream()
                .peek(this::verifyAccountInvariant)
                .map(AccountResponse::from)
                .toList();
    }

    public TransferResponse getTransfer(UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new BankException(
                        ErrorCode.TRANSFER_NOT_FOUND,
                        "Transfer %s not found".formatted(transferId)
                ));
        verifyTransferInvariant(transfer);
        return TransferResponse.from(transfer);
    }

    public List<TransferResponse> getTransfers() {
        return transferRepository.findAll().stream()
                .peek(this::verifyTransferInvariant)
                .map(TransferResponse::from)
                .toList();
    }

    @Verified(id = "BANK-DEPOSIT", description = "Increase active account balance by a positive amount")
    @Precondition("account exists, account is ACTIVE, amount is positive")
    @Postcondition("new balance equals old balance plus amount")
    public synchronized AccountResponse deposit(Long accountId, MoneyOperationRequest request) {
        Contract.requires(request != null, "deposit request must be defined");
        BigDecimal amount = MoneyRules.requirePositive(request.amount());
        Account account = requireAccount(accountId);
        ensureActive(account);
        verifyAccountInvariant(account);

        BigDecimal balanceBefore = account.getBalance();
        account.deposit(amount);
        ensureBalanceEquals(account.getBalance(), balanceBefore.add(amount), "Deposit postcondition failed");
        verifyAccountInvariant(account);

        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    @Verified(id = "BANK-WITHDRAW", description = "Decrease active account balance when funds are sufficient")
    @Precondition("account exists, account is ACTIVE, amount is positive and balance is sufficient")
    @Postcondition("new balance equals old balance minus amount and remains non-negative")
    public synchronized AccountResponse withdraw(Long accountId, MoneyOperationRequest request) {
        Contract.requires(request != null, "withdraw request must be defined");
        BigDecimal amount = MoneyRules.requirePositive(request.amount());
        Account account = requireAccount(accountId);
        ensureActive(account);
        ensureEnoughFunds(account, amount);
        verifyAccountInvariant(account);

        BigDecimal balanceBefore = account.getBalance();
        account.withdraw(amount);
        ensureBalanceEquals(account.getBalance(), balanceBefore.subtract(amount), "Withdraw postcondition failed");
        verifyAccountInvariant(account);

        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    @Verified(id = "BANK-TRANSFER", description = "Move money between two active same-currency accounts")
    @Precondition("source and target accounts are different, active, same-currency, and source has enough funds")
    @Postcondition("source decreases by amount, target increases by amount, and total balance is preserved")
    public synchronized TransferResponse transfer(TransferRequest request) {
        Contract.requires(request != null, "transfer request must be defined");
        Contract.requires(request.sourceAccountId() != null, "source account id must be defined");
        Contract.requires(request.targetAccountId() != null, "target account id must be defined");
        BigDecimal amount = MoneyRules.requirePositive(request.amount());
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new BankException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and target accounts must be different");
        }

        Account source = requireAccount(request.sourceAccountId());
        Account target = requireAccount(request.targetAccountId());
        ensureActive(source);
        ensureActive(target);
        ensureSameCurrency(source, target);
        ensureEnoughFunds(source, amount);
        verifyAccountInvariant(source);
        verifyAccountInvariant(target);

        BigDecimal sourceBalanceBefore = source.getBalance();
        BigDecimal targetBalanceBefore = target.getBalance();
        BigDecimal totalBefore = sourceBalanceBefore.add(targetBalanceBefore);

        source.withdraw(amount);
        target.deposit(amount);

        ensureBalanceEquals(source.getBalance(), sourceBalanceBefore.subtract(amount),
                "Transfer source postcondition failed");
        ensureBalanceEquals(target.getBalance(), targetBalanceBefore.add(amount),
                "Transfer target postcondition failed");
        ensureBalanceEquals(source.getBalance().add(target.getBalance()), totalBefore,
                "Transfer conservation postcondition failed");
        verifyAccountInvariant(source);
        verifyAccountInvariant(target);

        accountRepository.save(source);
        accountRepository.save(target);

        Transfer transfer = new Transfer(
                UUID.randomUUID(),
                source.getId(),
                target.getId(),
                amount,
                source.getCurrency(),
                TransferStatus.COMPLETED,
                Instant.now(clock)
        );
        verifyTransferInvariant(transfer);
        transferRepository.save(transfer);
        return TransferResponse.from(transfer);
    }

    @Verified(id = "BANK-BLOCK-ACCOUNT", description = "Move active account to BLOCKED status")
    @Precondition("account exists, account is not already BLOCKED, reason is defined")
    @Postcondition("account status is BLOCKED and block metadata is defined")
    public synchronized AccountResponse blockAccount(Long accountId, BlockAccountRequest request) {
        Contract.requires(request != null, "block account request must be defined");
        Account account = requireAccount(accountId);
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new BankException(ErrorCode.ACCOUNT_ALREADY_BLOCKED,
                    "Account %d is already blocked".formatted(accountId));
        }

        String reason = normalizeBlockReason(request.reason());
        account.block(reason, Instant.now(clock));
        verifyAccountInvariant(account);
        Contract.ensures(account.getStatus() == AccountStatus.BLOCKED, "account must be blocked");
        Contract.ensures(account.getBlockedAt() != null, "blocked account must have blockedAt");
        Contract.ensures(account.getBlockReason() != null && !account.getBlockReason().isBlank(),
                "blocked account must have reason");
        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    private Account requireAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BankException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account %s not found".formatted(accountId)
                ));
        verifyAccountInvariant(account);
        return account;
    }

    private void ensureActive(Account account) {
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new BankException(ErrorCode.ACCOUNT_BLOCKED, "Account %d is blocked".formatted(account.getId()));
        }
        Contract.ensures(account.getStatus() == AccountStatus.ACTIVE, "account must be active");
    }

    private void ensureEnoughFunds(Account account, BigDecimal amount) {
        Contract.requires(BigDecimalSpec.isPositive(amount), "operation amount must be positive");
        Contract.invariant(BigDecimalSpec.isNonNegative(account.getBalance()), "account balance must be non-negative");
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BankException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Account %d has insufficient funds".formatted(account.getId()));
        }
        Contract.ensures(account.getBalance().compareTo(amount) >= 0, "account must have enough funds");
    }

    private void ensureSameCurrency(Account source, Account target) {
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new BankException(ErrorCode.CURRENCY_MISMATCH, "Transfer between different currencies is not allowed");
        }
        Contract.ensures(source.getCurrency().equals(target.getCurrency()), "transfer accounts must have same currency");
    }

    private void ensureBalanceEquals(BigDecimal actual, BigDecimal expected, String message) {
        Contract.ensures(BigDecimalSpec.isEqualByValue(actual, expected), message);
        Contract.ensures(BigDecimalSpec.isNonNegative(actual), "Balance must not become negative");
    }

    private String normalizeOwnerName(String ownerName) {
        if (ownerName == null || ownerName.isBlank() || ownerName.length() > 100) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_DATA, "Owner name must be 1-100 characters long");
        }
        String normalized = ownerName.trim();
        Contract.ensures(!normalized.isBlank() && normalized.length() <= 100,
                "normalized owner name must be 1-100 characters");
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_DATA, "Currency must not be null");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_DATA, "Currency must be a 3-letter uppercase code");
        }
        Contract.ensures(normalized.matches("[A-Z]{3}"), "normalized currency must be a 3-letter uppercase code");
        return normalized;
    }

    private String normalizeBlockReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new BankException(ErrorCode.INVALID_BLOCK_REASON, "Block reason must be 1-255 characters long");
        }
        String normalized = reason.trim();
        Contract.ensures(!normalized.isBlank() && normalized.length() <= 255,
                "normalized block reason must be 1-255 characters");
        return normalized;
    }

    private void verifyAccountInvariant(Account account) {
        Contract.invariant(account != null, "account must be defined");
        Contract.invariant(account.getId() != null, "account id must be defined");
        Contract.invariant(account.getOwnerName() != null && !account.getOwnerName().isBlank(),
                "account owner must be defined");
        Contract.invariant(account.getCurrency() != null && account.getCurrency().matches("[A-Z]{3}"),
                "account currency must be ISO-like code");
        Contract.invariant(BigDecimalSpec.isNonNegative(account.getBalance()), "account balance must be non-negative");
        Contract.invariant(account.getStatus() != null, "account status must be defined");
        Contract.invariant(account.getCreatedAt() != null, "account creation time must be defined");
        if (account.getStatus() == AccountStatus.BLOCKED) {
            Contract.invariant(account.getBlockedAt() != null, "blocked account must have blockedAt");
            Contract.invariant(account.getBlockReason() != null && !account.getBlockReason().isBlank(),
                    "blocked account must have reason");
        }
    }

    private void verifyTransferInvariant(Transfer transfer) {
        Contract.invariant(transfer != null, "transfer must be defined");
        Contract.invariant(transfer.getId() != null, "transfer id must be defined");
        Contract.invariant(transfer.getSourceAccountId() != null, "transfer source account must be defined");
        Contract.invariant(transfer.getTargetAccountId() != null, "transfer target account must be defined");
        Contract.invariant(!transfer.getSourceAccountId().equals(transfer.getTargetAccountId()),
                "transfer accounts must be different");
        Contract.invariant(BigDecimalSpec.isPositive(transfer.getAmount()), "transfer amount must be positive");
        Contract.invariant(transfer.getCurrency() != null && transfer.getCurrency().matches("[A-Z]{3}"),
                "transfer currency must be ISO-like code");
        Contract.invariant(transfer.getStatus() == TransferStatus.COMPLETED, "transfer must be completed");
        Contract.invariant(transfer.getCreatedAt() != null, "transfer creation time must be defined");
    }
}

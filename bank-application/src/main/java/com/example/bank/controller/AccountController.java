package com.example.bank.controller;

import com.example.bank.dto.AccountCreateRequest;
import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.BlockAccountRequest;
import com.example.bank.dto.MoneyOperationRequest;
import com.example.bank.service.BankService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final BankService bankService;

    public AccountController(BankService bankService) {
        this.bankService = bankService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountCreateRequest request) {
        return bankService.createAccount(request);
    }

    @GetMapping
    public List<AccountResponse> getAccounts() {
        return bankService.getAccounts();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable Long accountId) {
        return bankService.getAccount(accountId);
    }

    @PostMapping("/{accountId}/deposit")
    public AccountResponse deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyOperationRequest request
    ) {
        return bankService.deposit(accountId, request);
    }

    @PostMapping("/{accountId}/withdraw")
    public AccountResponse withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyOperationRequest request
    ) {
        return bankService.withdraw(accountId, request);
    }

    @PostMapping("/{accountId}/block")
    public AccountResponse blockAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody BlockAccountRequest request
    ) {
        return bankService.blockAccount(accountId, request);
    }
}

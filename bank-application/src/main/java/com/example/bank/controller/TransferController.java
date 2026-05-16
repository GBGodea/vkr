package com.example.bank.controller;

import com.example.bank.dto.TransferRequest;
import com.example.bank.dto.TransferResponse;
import com.example.bank.service.BankService;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/transfers")
public class TransferController {

    private final BankService bankService;

    public TransferController(BankService bankService) {
        this.bankService = bankService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return bankService.transfer(request);
    }

    @GetMapping
    public List<TransferResponse> getTransfers() {
        return bankService.getTransfers();
    }

    @GetMapping("/{transferId}")
    public TransferResponse getTransfer(@PathVariable UUID transferId) {
        return bankService.getTransfer(transferId);
    }
}

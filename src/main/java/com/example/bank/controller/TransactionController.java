package com.example.bank.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.bank.service.TransactionService;
import com.example.bank.service.TransactionView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;




@RestController
@RequestMapping("/api/transactions")

public class TransactionController {
    private final TransactionService transactionService;
    public TransactionController(TransactionService transactionService){
        this.transactionService = transactionService;
    }
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView deposit(@Valid @RequestBody DepositRequest request, Authentication authentication){
        return transactionService.deposit(authentication.getName(), request.accountNumber(), request.amount(), request.description());
    }
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED) 
    public TransactionView withdraw(@Valid @RequestBody WithdrawRequest request, Authentication authentication){
        return transactionService.withdraw(authentication.getName(), request.accountNumber(), request.amount(), request.description());
    }
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView transfer(@Valid @RequestBody TransferRequest request,@RequestHeader(value="IdempotencyKey", required=false) String idempotencyKey, Authentication authentication){
        return transactionService.transfer(authentication.getName(), request.fromAccountNumber(), request.toAccountNumber(), request.amount(), request.description(), idempotencyKey);
    }
    @GetMapping
    public List<TransactionView> history(@RequestParam("accountId") Long accountId, @RequestParam(value="page", defaultValue="0")int page, @RequestParam(value="size", defaultValue="20")int size, Authentication authentication){
        return transactionService.history(authentication.getName(), accountId, page, size);
    }
    public record DepositRequest(@NotNull String accountNumber, @NotNull @Positive BigDecimal amount, @Size(max=255) String description){}
    public record WithdrawRequest(@NotNull String accountNumber, @NotNull @Positive BigDecimal amount, @Size(max=255) String description){}
    public record TransferRequest(@NotNull String fromAccountNumber, @NotNull String toAccountNumber,@NotNull @Positive  BigDecimal amount, @Size(max=255) String description){}
}

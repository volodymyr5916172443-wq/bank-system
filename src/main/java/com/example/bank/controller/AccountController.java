package com.example.bank.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.bank.entity.Account;
import com.example.bank.entity.AccountStatus;
import com.example.bank.entity.Currency;
import com.example.bank.service.AccountService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/accounts") 
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService){
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request, Authentication authentication){
        Account account = accountService.createAccount(authentication.getName(), request.currency());
        return AccountResponse.from(account);
    }
    @GetMapping
    public List<AccountResponse> myAccounts(Authentication authentication){
        return accountService.getAccounts(authentication.getName()).stream().map(AccountResponse::from).toList();
    } 
    public record CreateAccountRequest(@NotNull Currency currency){}
    public record AccountResponse(Long id, String accountNumber, Currency currency, BigDecimal balance, AccountStatus status, Instant createdAt){
        static AccountResponse from(Account a){
            return new AccountResponse(a.getId(), a.getAccountNumber(), a.getCurrency(), a.getBalance(), a.getStatus(), a.getCreatedAt());
        }
    }
}

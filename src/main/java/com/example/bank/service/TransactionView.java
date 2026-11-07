package com.example.bank.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.bank.entity.BankTransaction;
import com.example.bank.entity.Currency;
import com.example.bank.entity.TransactionStatus;
import com.example.bank.entity.TransactionType;

public record TransactionView(Long id, TransactionType type, TransactionStatus status, BigDecimal amount, Currency currency, String fromAccountNumber, String toAccountNumber, String description, Instant createdAd){
    static TransactionView from(BankTransaction t){
        return new TransactionView(t.getId(), t.getType(), t.getStatus(), t.getAmount(), t.getCurrency(), t.getFromAccount()==null ? null : t.getFromAccount().getAccountNumber(), t.getToAccount()==null ? null:t.getToAccount().getAccountNumber(), t.getDescription(), t.getCreatedAt());
    }
}

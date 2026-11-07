package com.example.bank.exception;

import java.math.BigDecimal;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import com.example.bank.entity.TransactionType;

public class TransactionRejectedException extends ResponseStatusException{
    private final String code;
    private final FailedAttempt attempt;

    public TransactionRejectedException(String message, String code, FailedAttempt attempt){
        super(HttpStatusCode.valueOf(422), message);
        this.code = code;
        this.attempt = attempt;
    }
    public String getCode(){
        return code;
    }
    public FailedAttempt getAttempt(){
        return attempt;
    }

    public record FailedAttempt(TransactionType type, Long actorId, Long fromAccountId, Long toAccountId, BigDecimal amount, String description){}
}

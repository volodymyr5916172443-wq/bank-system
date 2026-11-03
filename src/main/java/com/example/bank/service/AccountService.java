package com.example.bank.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.bank.entity.Account;
import com.example.bank.entity.Currency;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.UserRepository;

@Service 
public class AccountService {
    private static final int MAX_NUMBER_ATTEMPTS = 5;
    private final UserRepository users;
    private final AccountRepository accounts;
    public AccountService(AccountRepository accounts, UserRepository users){
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional
     public Account createAccount(String ownerEmail, Currency currency){
        if(currency == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency not specified");
        }
        User user = users.findByEmail(ownerEmail.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        for(int i =0;i<MAX_NUMBER_ATTEMPTS;i++){
            String number = AccountNumberGenerator.generate();
            if(!accounts.existsByAccountNumber(number)){
                return accounts.saveAndFlush(new Account(user, number, currency));
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate account number");
     }
     @Transactional(readOnly=true)
     public List<Account> getAccounts(String ownerEmail){
        return accounts.findByUserEmailOrderByIdAsc(ownerEmail.trim().toLowerCase(Locale.ROOT));
     }
}

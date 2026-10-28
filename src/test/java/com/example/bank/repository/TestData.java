package com.example.bank.repository;

import java.math.BigDecimal;

import com.example.bank.entity.Account;
import com.example.bank.entity.Currency;
import com.example.bank.entity.User;

final class TestData {

    private TestData() {
    }

    static User user(UserRepository users, String email) {
        return users.saveAndFlush(new User(email, "test-password-hash", "Test User"));
    }

    static Account account(AccountRepository accounts, User owner, String number, String balance) {
        Account account = new Account(owner, number, Currency.UAH);
        account.setBalance(new BigDecimal(balance));
        return accounts.saveAndFlush(account);
    }
}
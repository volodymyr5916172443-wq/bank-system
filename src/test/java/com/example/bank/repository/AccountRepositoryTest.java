package com.example.bank.repository;

import com.example.bank.entity.Account;
import com.example.bank.entity.AccountStatus;
import com.example.bank.entity.Currency;
import com.example.bank.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountRepositoryTest {

    @Autowired
    AccountRepository accounts;
    @Autowired
    UserRepository users;
    @Autowired
    EntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestData.user(users, "owner@test.com");
    }

    @Test
    void savesAccountAndFindsItByUser() {
        TestData.account(accounts, user, "ACC-TEST-0001", "100.50");
        em.clear(); 

        List<Account> found = accounts.findByUserId(user.getId());

        assertThat(found).hasSize(1);
        Account account = found.get(0);
        assertThat(account.getBalance()).isEqualByComparingTo("100.50");
        assertThat(account.getCurrency()).isEqualTo(Currency.UAH);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getVersion()).isZero();
        assertThat(account.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsNegativeBalance() {
        Account account = new Account(user, "ACC-TEST-0002", Currency.UAH);
        account.setBalance(new BigDecimal("-0.01"));

        assertThatThrownBy(() -> accounts.saveAndFlush(account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateAccountNumber() {
        TestData.account(accounts, user, "ACC-TEST-0003", "0");

        assertThatThrownBy(() -> TestData.account(accounts, user, "ACC-TEST-0003", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockQueryReturnsAccountsOrderedById() {
        Account first = TestData.account(accounts, user, "ACC-TEST-0010", "0");
        Account second = TestData.account(accounts, user, "ACC-TEST-0011", "0");

        List<Account> locked = accounts.findAllByIdForUpdate(List.of(second.getId(), first.getId()));

        assertThat(locked).extracting(Account::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void staleUpdateIsRejectedByVersionCheck() {
        Account account = TestData.account(accounts, user, "ACC-TEST-0020", "100.00");

        
        em.createNativeQuery("update accounts set version = version + 1 where id = :id")
                .setParameter("id", account.getId())
                .executeUpdate();

        account.setBalance(new BigDecimal("50.00"));

        assertThatThrownBy(() -> accounts.saveAndFlush(account))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
package com.example.bank.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.bank.entity.Account;
import com.example.bank.entity.Currency;
import com.example.bank.entity.TransactionStatus;
import com.example.bank.entity.TransactionType;
import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionServiceTest {

    @Autowired
    TransactionService service;
    @Autowired
    UserRepository users;
    @Autowired
    AccountRepository accounts;
    @Autowired
    EntityManager em;

    private User admin;
    private User anna;
    private User boris;
    private Account annaUah;   
    private Account borisUah;  
    private Account borisUsd; 

    @BeforeEach
    void setUp() {
        admin = saveUser("admin@test.com", UserRole.ADMIN);
        anna = saveUser("anna@test.com", UserRole.CUSTOMER);
        boris = saveUser("boris@test.com", UserRole.CUSTOMER);
        annaUah = saveAccount(anna, Currency.UAH, "1000.00");
        borisUah = saveAccount(boris, Currency.UAH, "0");
        borisUsd = saveAccount(boris, Currency.USD, "0");
    }

    @Test
    void adminDepositIncreasesBalanceAndIsJournaled() {
        TransactionView view = service.deposit(admin.getEmail(), annaUah.getAccountNumber(),
                new BigDecimal("150.50"), "cash in");

        assertThat(view.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(view.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(view.fromAccountNumber()).isNull();
        assertThat(view.toAccountNumber()).isEqualTo(annaUah.getAccountNumber());
        assertThat(balanceOf(annaUah)).isEqualByComparingTo("1150.50");
    }

    @Test
    void customerCannotDeposit() {
        assertStatus(() -> service.deposit(anna.getEmail(), annaUah.getAccountNumber(),
                new BigDecimal("100.00"), "free money"), 403);
    }


    @Test
    void withdrawalDecreasesBalance() {
        TransactionView view = service.withdraw(anna.getEmail(), annaUah.getAccountNumber(),
                new BigDecimal("200.00"), "atm");

        assertThat(view.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(view.toAccountNumber()).isNull();
        assertThat(balanceOf(annaUah)).isEqualByComparingTo("800.00");
    }

    @Test
    void cannotWithdrawFromSomeoneElsesAccount() {
        assertStatus(() -> service.withdraw(boris.getEmail(), annaUah.getAccountNumber(),
                new BigDecimal("1.00"), "steal"), 403);
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        TransactionView view = service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                borisUah.getAccountNumber(), new BigDecimal("250.00"), "rent", null);

        assertThat(view.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(view.fromAccountNumber()).isEqualTo(annaUah.getAccountNumber());
        assertThat(view.toAccountNumber()).isEqualTo(borisUah.getAccountNumber());
        assertThat(balanceOf(annaUah)).isEqualByComparingTo("750.00");
        assertThat(balanceOf(borisUah)).isEqualByComparingTo("250.00");
    }

    @Test
    void cannotTransferFromSomeoneElsesAccount() {
        assertStatus(() -> service.transfer(boris.getEmail(), annaUah.getAccountNumber(),
                borisUah.getAccountNumber(), new BigDecimal("10.00"), "steal", null), 403);
    }

    @Test
    void cannotTransferToTheSameAccount() {
        assertStatus(() -> service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                annaUah.getAccountNumber(), new BigDecimal("10.00"), "loop", null), 400);
    }

    @Test
    void cannotTransferBetweenDifferentCurrencies() {
        assertStatus(() -> service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                borisUsd.getAccountNumber(), new BigDecimal("10.00"), "fx", null), 422);
    }

    @Test
    void unknownAccountIsNotFound() {
        assertStatus(() -> service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                "UA000000000000000000000000000", new BigDecimal("10.00"), "nowhere", null), 404);
    }

    @Test
    void repeatedTransferWithSameIdempotencyKeyIsAppliedOnce() {
        TransactionView first = service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                borisUah.getAccountNumber(), new BigDecimal("100.00"), "rent", "key-1");
        TransactionView second = service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                borisUah.getAccountNumber(), new BigDecimal("100.00"), "rent", "key-1");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(balanceOf(annaUah)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(borisUah)).isEqualByComparingTo("100.00");
    }

    @Test
    void amountMustBePositive() {
        assertStatus(() -> service.withdraw(anna.getEmail(), annaUah.getAccountNumber(), BigDecimal.ZERO, "x"), 400);
        assertStatus(() -> service.withdraw(anna.getEmail(), annaUah.getAccountNumber(), new BigDecimal("-5"), "x"), 400);
        assertStatus(() -> service.withdraw(anna.getEmail(), annaUah.getAccountNumber(), null, "x"), 400);
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() {
        assertStatus(() -> service.withdraw(anna.getEmail(), annaUah.getAccountNumber(),
                new BigDecimal("10.001"), "x"), 400);
    }


    @Test
    void historyShowsBothSidesNewestFirst() {
        TransactionView deposit = service.deposit(admin.getEmail(), annaUah.getAccountNumber(),
                new BigDecimal("10.00"), "in");
        TransactionView transfer = service.transfer(anna.getEmail(), annaUah.getAccountNumber(),
                borisUah.getAccountNumber(), new BigDecimal("5.00"), "out", null);

        List<TransactionView> annaHistory = service.history(anna.getEmail(), annaUah.getId(), 0, 10);
        List<TransactionView> borisHistory = service.history(boris.getEmail(), borisUah.getId(), 0, 10);

        assertThat(annaHistory).extracting(TransactionView::id).containsExactly(transfer.id(), deposit.id());
        assertThat(borisHistory).extracting(TransactionView::id).containsExactly(transfer.id());
    }

    @Test
    void customerCannotReadHistoryOfSomeoneElsesAccount() {
        assertStatus(() -> service.history(boris.getEmail(), annaUah.getId(), 0, 10), 403);
    }


    private User saveUser(String email, UserRole role) {
        User user = new User(email, "hash", "Test User");
        user.changeRole(role);
        return users.saveAndFlush(user);
    }

    private Account saveAccount(User owner, Currency currency, String balance) {
        Account account = new Account(owner, AccountNumberGenerator.generate(), currency);
        account.setBalance(new BigDecimal(balance));
        return accounts.saveAndFlush(account);
    }

    private BigDecimal balanceOf(Account account) {
        em.flush();
        em.clear();
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    private static void assertStatus(ThrowingCallable call, int expectedStatus) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(expectedStatus));
    }
}
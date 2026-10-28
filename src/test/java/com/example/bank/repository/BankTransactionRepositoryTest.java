
package com.example.bank.repository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.bank.entity.Account;
import com.example.bank.entity.BankTransaction;
import com.example.bank.entity.Currency;
import com.example.bank.entity.TransactionStatus;
import com.example.bank.entity.TransactionType;
import com.example.bank.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BankTransactionRepositoryTest {

    @Autowired
    BankTransactionRepository transactions;

    @Autowired
    AccountRepository accounts;

    @Autowired
    UserRepository users;

    @Autowired
    EntityManager em;

    private User user;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        user = TestData.user(users, "owner@test.com");

        accountA = TestData.account(
                accounts, user, "ACC-TEST-A", "1000.00"
        );

        accountB = TestData.account(
                accounts, user, "ACC-TEST-B", "0"
        );
    }

    @Test
    void transferAppearsInHistoryOfBothAccounts() {
        BankTransaction tx = BankTransaction.transaction(
                accountA,
                accountB,
                new BigDecimal("25.00"),
                Currency.UAH,
                TransactionType.TRANSFER,
                user,
                "test",
                "transfer-history-1"
        );

        transactions.saveAndFlush(tx);
        em.clear();

        var historyA = transactions.findHistoryByAccountId(
                accountA.getId(), PageRequest.of(0, 10)
        );

        var historyB = transactions.findHistoryByAccountId(
                accountB.getId(), PageRequest.of(0, 10)
        );

        assertThat(historyA.getContent()).hasSize(1);
        assertThat(historyB.getContent()).hasSize(1);

        assertThat(historyA.getContent().get(0).getType())
                .isEqualTo(TransactionType.TRANSFER);

        assertThat(historyA.getContent().get(0).getAmount())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void historyIsSortedNewestFirst() {
        BankTransaction first = transactions.saveAndFlush(
                BankTransaction.deposit(
                        accountA, new BigDecimal("10.00"),
                        user, "first", "history-deposit-1"
                )
        );

        BankTransaction second = transactions.saveAndFlush(
                BankTransaction.deposit(
                        accountA, new BigDecimal("20.00"),
                        user, "second", "history-deposit-2"
                )
        );

        em.clear();

        List<BankTransaction> history = transactions
                .findHistoryByAccountId(
                        accountA.getId(), PageRequest.of(0, 10)
                )
                .getContent();

        assertThat(history)
                .extracting(BankTransaction::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void failedTransactionKeepsReason() {
        BankTransaction tx = BankTransaction.transaction(
                accountA,
                accountB,
                new BigDecimal("999999.00"),
                Currency.UAH,
                TransactionType.TRANSFER,
                user,
                "too much",
                "failed-transfer-1"
        );

        tx.markFailed("INSUFFICIENT_FUNDS");

        transactions.saveAndFlush(tx);
        em.clear();

        BankTransaction loaded = transactions.findById(tx.getId())
                .orElseThrow();

        assertThat(loaded.getStatus())
                .isEqualTo(TransactionStatus.FAILED);

        assertThat(loaded.getFailureReason())
                .isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void findsTransactionByIdempotencyKey() {
        BankTransaction tx = BankTransaction.deposit(
                accountA, new BigDecimal("5.00"),
                user, "dep", "key-1"
        );

        transactions.saveAndFlush(tx);

        assertThat(
                transactions.findByInitiatedByIdAndIdempotencyKey(
                        user.getId(), "key-1"
                )
        ).isPresent();

        assertThat(
                transactions.findByInitiatedByIdAndIdempotencyKey(
                        user.getId(), "other"
                )
        ).isEmpty();
    }

    @Test
    void rejectsDuplicateIdempotencyKey() {
        transactions.saveAndFlush(
                BankTransaction.deposit(
                        accountA, new BigDecimal("5.00"),
                        user, "one", "key-dup"
                )
        );

        BankTransaction second = BankTransaction.deposit(
                accountA, new BigDecimal("5.00"),
                user, "two", "key-dup"
        );

        assertThatThrownBy(() -> transactions.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void factoryRejectsTransferBetweenDifferentCurrencies() {
        Account usd = new Account(
                user, "ACC-TEST-USD", Currency.USD
        );

        assertThatThrownBy(
                () -> BankTransaction.transaction(
                        accountA,
                        usd,
                        new BigDecimal("10.00"),
                        Currency.UAH,
                        TransactionType.TRANSFER,
                        user,
                        "currency test",
                        "currency-test-1"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

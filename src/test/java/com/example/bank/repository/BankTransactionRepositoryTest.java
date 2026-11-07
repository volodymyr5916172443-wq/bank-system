
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

    // Helper method for assigning idempotency key
    private BankTransaction withKey(
            BankTransaction tx,
            String key
    ) {
        tx.setIdempotencyKey(key);
        return tx;
    }

    @Test
    void transferAppearsInHistoryOfBothAccounts() {

        BankTransaction tx = withKey(
                BankTransaction.transaction(
                        accountA,
                        accountB,
                        new BigDecimal("25.00"),
                        user,
                        "test"
                ),
                "transfer-history-1"
        );

        transactions.saveAndFlush(tx);
        em.clear();

        var historyA = transactions.findHistoryByAccountId(
                accountA.getId(),
                PageRequest.of(0, 10)
        );

        var historyB = transactions.findHistoryByAccountId(
                accountB.getId(),
                PageRequest.of(0, 10)
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
                withKey(
                        BankTransaction.deposit(
                                accountA,
                                new BigDecimal("10.00"),
                                user,
                                "first"
                        ),
                        "history-deposit-1"
                )
        );

        BankTransaction second = transactions.saveAndFlush(
                withKey(
                        BankTransaction.deposit(
                                accountA,
                                new BigDecimal("20.00"),
                                user,
                                "second"
                        ),
                        "history-deposit-2"
                )
        );

        em.clear();

        List<BankTransaction> history = transactions
                .findHistoryByAccountId(
                        accountA.getId(),
                        PageRequest.of(0, 10)
                )
                .getContent();

        assertThat(history)
                .extracting(BankTransaction::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void failedTransactionKeepsReason() {

        BankTransaction tx = withKey(
                BankTransaction.transaction(
                        accountA,
                        accountB,
                        new BigDecimal("999999.00"),
                        user,
                        "too much"
                ),
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

        BankTransaction tx = withKey(
                BankTransaction.deposit(
                        accountA,
                        new BigDecimal("5.00"),
                        user,
                        "dep"
                ),
                "key-1"
        );

        transactions.saveAndFlush(tx);

        assertThat(
                transactions.findByInitiatedByIdAndIdempotencyKey(
                        user.getId(),
                        "key-1"
                )
        ).isPresent();

        assertThat(
                transactions.findByInitiatedByIdAndIdempotencyKey(
                        user.getId(),
                        "other"
                )
        ).isEmpty();
    }

    @Test
    void rejectsDuplicateIdempotencyKey() {

        transactions.saveAndFlush(
                withKey(
                        BankTransaction.deposit(
                                accountA,
                                new BigDecimal("5.00"),
                                user,
                                "one"
                        ),
                        "key-dup"
                )
        );

        BankTransaction second = withKey(
                BankTransaction.deposit(
                        accountA,
                        new BigDecimal("5.00"),
                        user,
                        "two"
                ),
                "key-dup"
        );

        assertThatThrownBy(
                () -> transactions.saveAndFlush(second)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void factoryRejectsTransferBetweenDifferentCurrencies() {

        Account usd = new Account(
                user,
                "ACC-TEST-USD",
                Currency.USD
        );

        assertThatThrownBy(
                () -> BankTransaction.transaction(
                        accountA,
                        usd,
                        new BigDecimal("10.00"),
                        user,
                        "currency test"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void depositCreatesCorrectTransaction() {

        BankTransaction tx = withKey(
                BankTransaction.deposit(
                        accountA,
                        new BigDecimal("100.00"),
                        user,
                        "test deposit"
                ),
                "deposit-test-1"
        );

        assertThat(tx.getType())
                .isEqualTo(TransactionType.DEPOSIT);

        assertThat(tx.getToAccount())
                .isEqualTo(accountA);

        assertThat(tx.getFromAccount())
                .isNull();

        assertThat(tx.getAmount())
                .isEqualByComparingTo("100.00");

        assertThat(tx.getCurrency())
                .isEqualTo(Currency.UAH);
    }

    @Test
    void withdrawalCreatesCorrectTransaction() {

        BankTransaction tx = withKey(
                BankTransaction.withdrawal(
                        accountA,
                        new BigDecimal("50.00"),
                        user,
                        "test withdrawal"
                ),
                "withdrawal-test-1"
        );

        assertThat(tx.getType())
                .isEqualTo(TransactionType.WITHDRAWAL);

        assertThat(tx.getFromAccount())
                .isEqualTo(accountA);

        assertThat(tx.getToAccount())
                .isNull();
    }

    @Test
    void rejectsNonPositiveAmount() {

        assertThatThrownBy(
                () -> BankTransaction.deposit(
                        accountA,
                        BigDecimal.ZERO,
                        user,
                        "invalid deposit"
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> BankTransaction.withdrawal(
                        accountA,
                        new BigDecimal("-10.00"),
                        user,
                        "invalid withdrawal"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTransferToSameAccount() {

        assertThatThrownBy(
                () -> BankTransaction.transaction(
                        accountA,
                        accountA,
                        new BigDecimal("10.00"),
                        user,
                        "same account"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.example.bank.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.bank.entity.Account;
import com.example.bank.entity.AccountStatus;
import com.example.bank.entity.Currency;
import com.example.bank.entity.User;
import com.example.bank.exception.TransactionRejectedException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class TransactionConcurrencyTest {

    private static final String EMAIL_PATTERN = "conc-%@test.com";

    @Autowired
    TransactionService service;
    @Autowired
    UserRepository users;
    @Autowired
    AccountRepository accounts;
    @Autowired
    JdbcTemplate jdbc;

    private User anna;
    private User boris;

    @BeforeEach
    void setUp() {
        cleanUp();
        anna = users.saveAndFlush(new User("conc-anna@test.com", "hash", "Anna"));
        boris = users.saveAndFlush(new User("conc-boris@test.com", "hash", "Boris"));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void oppositeTransfersDoNotDeadlockAndKeepTotalBalance() throws Exception {
        Account a = account(anna, "10000.00");
        Account b = account(boris, "10000.00");

      
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> repeat(25, () -> service.transfer(anna.getEmail(), a.getAccountNumber(),
                    b.getAccountNumber(), new BigDecimal("1.00"), "a->b", null)));
            tasks.add(() -> repeat(25, () -> service.transfer(boris.getEmail(), b.getAccountNumber(),
                    a.getAccountNumber(), new BigDecimal("2.00"), "b->a", null)));
        }

        List<Throwable> errors = runInParallel(8, tasks);

        assertThat(errors).isEmpty();
        assertThat(balanceOf(a)).isEqualByComparingTo("10100.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("9900.00");
        assertThat(balanceOf(a).add(balanceOf(b))).isEqualByComparingTo("20000.00");
        assertThat(countOf(a, "SUCCESS")).isEqualTo(200);
    }

    @Test
    void parallelWithdrawalsNeverMakeBalanceNegative() throws Exception {
        Account wallet = account(anna, "100.00");

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> service.withdraw(anna.getEmail(), wallet.getAccountNumber(),
                    new BigDecimal("10.00"), "atm"));
        }

        List<Throwable> errors = runInParallel(8, tasks);

        assertThat(errors).hasSize(10)
                .allSatisfy(e -> assertThat(e).isInstanceOf(TransactionRejectedException.class));
        assertThat(balanceOf(wallet)).isEqualByComparingTo("0.00");
        assertThat(countOf(wallet, "SUCCESS")).isEqualTo(10);
        assertThat(countOf(wallet, "FAILED")).isEqualTo(10);
    }

    @Test
    void concurrentRetriesWithSameIdempotencyKeyAreAppliedOnce() throws Exception {
        Account a = account(anna, "500.00");
        Account b = account(boris, "0");
        Set<Long> transactionIds = ConcurrentHashMap.newKeySet();

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> transactionIds.add(service.transfer(anna.getEmail(), a.getAccountNumber(),
                    b.getAccountNumber(), new BigDecimal("100.00"), "retry", "key-1").id()));
        }

        List<Throwable> errors = runInParallel(5, tasks);

        assertThat(errors).isEmpty();
        assertThat(transactionIds).hasSize(1);
        assertThat(balanceOf(a)).isEqualByComparingTo("400.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("100.00");
        assertThat(countOf(a, "SUCCESS")).isEqualTo(1);
    }

    @Test
    void rejectedTransferLeavesBalancesUntouchedAndIsLoggedAsFailed() {
        Account a = account(anna, "50.00");
        Account b = account(boris, "0");

        assertThatThrownBy(() -> service.transfer(anna.getEmail(), a.getAccountNumber(),
                b.getAccountNumber(), new BigDecimal("80.00"), "too much", null))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(a)).isEqualByComparingTo("50.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("0.00");
        assertThat(countOf(a, "SUCCESS")).isZero();
        assertThat(countOf(a, "FAILED")).isEqualTo(1);

        String reason = jdbc.queryForObject(
                "select failure_reason from transactions where from_account_id = ?", String.class, a.getId());
        assertThat(reason).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void blockedAccountRejectsOperationsAndIsLoggedAsFailed() {
        Account blocked = account(anna, "100.00");
        blocked.setStatus(AccountStatus.BLOCKED);
        accounts.saveAndFlush(blocked);

        assertThatThrownBy(() -> service.withdraw(anna.getEmail(), blocked.getAccountNumber(),
                new BigDecimal("10.00"), "atm"))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_BLOCKED"));

        assertThat(balanceOf(blocked)).isEqualByComparingTo("100.00");
        assertThat(countOf(blocked, "FAILED")).isEqualTo(1);
    }


    /** Запускает задачи одновременно (по сигналу) и возвращает все пойманные исключения. */
    private static List<Throwable> runInParallel(int threads, List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Runnable task : tasks) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                .as("задачи не завершились за 60 секунд: возможна взаимная блокировка (deadlock)")
                .isTrue();
        return errors;
    }

    private static void repeat(int times, Runnable action) {
        for (int i = 0; i < times; i++) {
            action.run();
        }
    }

    private Account account(User owner, String balance) {
        Account account = new Account(owner, AccountNumberGenerator.generate(), Currency.UAH);
        account.setBalance(new BigDecimal(balance));
        return accounts.saveAndFlush(account);
    }

    private BigDecimal balanceOf(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    private int countOf(Account account, String status) {
        Integer count = jdbc.queryForObject(
                "select count(*) from transactions where status = ? and (from_account_id = ? or to_account_id = ?)",
                Integer.class, status, account.getId(), account.getId());
        return count == null ? 0 : count;
    }

    private void cleanUp() {
        jdbc.update("delete from transactions where initiated_by in (select id from users where email like ?)",
                EMAIL_PATTERN);
        jdbc.update("delete from accounts where user_id in (select id from users where email like ?)",
                EMAIL_PATTERN);
        jdbc.update("delete from users where email like ?", EMAIL_PATTERN);
    }
}
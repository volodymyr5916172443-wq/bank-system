package com.example.bank.service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.bank.entity.Account;
import com.example.bank.entity.AccountStatus;
import com.example.bank.entity.BankTransaction;
import com.example.bank.entity.TransactionType;
import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.exception.TransactionRejectedException;
import com.example.bank.exception.TransactionRejectedException.FailedAttempt;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.BankTransactionRepository;
import com.example.bank.repository.UserRepository;


@Service 
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000.00");
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final TransactionTemplate tx;
    private final TransactionTemplate failureTx;

    public TransactionService(AccountRepository accounts, UserRepository users, BankTransactionRepository transactions, PlatformTransactionManager transactionManager){
        this.accounts = accounts;
        this.users = users;
        this.transactions = transactions;
        this.tx = new TransactionTemplate(transactionManager);
        this.failureTx = new TransactionTemplate(transactionManager);
        this.failureTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    
    public TransactionView deposit(String actorEmail, String accountNumber, BigDecimal amount, String description){
        BigDecimal value = normalizeAmount(amount);
        return runWithFailureLog(()->doDeposit(actorEmail, accountNumber, value, description));
    }
    public TransactionView withdraw(String actorEmail, String accountNumber, BigDecimal amount, String description){
        BigDecimal value = normalizeAmount(amount);
        return runWithFailureLog(()->doWithdrawal(actorEmail, accountNumber, value, description));
    }
    public TransactionView transfer(String actorEmail, String fromNumber, String toNumber, BigDecimal amount, String description, String idempotencyKey){
        BigDecimal value = normalizeAmount(amount);
        String key = normalizeKey(idempotencyKey);
        return runWithFailureLog(()->doTransfer(actorEmail, fromNumber, toNumber, value, description, key));
    }
    @Transactional(readOnly = true)
    public List<TransactionView> history(String actorEmail, Long accountId, int page, int size){
        User actor = requireUser(actorEmail);
        Account account = accounts.findById(accountId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        boolean isAdmin = actor.getUserRole() == UserRole.ADMIN;
        if(!isAdmin&&!account.getUser().getId().equals(actor.getId())){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The account belongs to another user");
        }
        int pageSize = Math.min(Math.max(size, 1), 100);
        return transactions.findHistoryByAccountId(accountId, PageRequest.of(Math.max(page, 0), pageSize)).map(TransactionView::from).getContent();
    }
    private TransactionView doDeposit(String actorEmail, String accountNumber, BigDecimal amount, String description){
        User actor = requireUser(actorEmail);
        if(actor.getUserRole() != UserRole.ADMIN){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can top up accounts");
        }
        Long accountId = requireAccountId(accountNumber);
        Account account = lock(accountId).get(accountId);
        FailedAttempt attempt = new FailedAttempt(TransactionType.DEPOSIT, actor.getId(), null, accountId, amount, description);
        requireActive(account, attempt);
        account.setBalance(account.getBalance().add(amount));
        return TransactionView.from(transactions.save(BankTransaction.deposit(account, amount, actor, description)));
    }
    private TransactionView doWithdrawal(String actorEmail, String accountNumber, BigDecimal amount, String description){
        User actor = requireUser(actorEmail);
        Long accountId = requireAccountId(accountNumber);
        Account account = lock(accountId).get(accountId);
        requireOwner(account, actor);
        FailedAttempt attempt = new FailedAttempt(TransactionType.WITHDRAWAL, actor.getId(), accountId,null, amount, description);
        requireActive(account, attempt);
        requireEnoughMoney(account, amount, attempt);
        account.setBalance(account.getBalance().subtract(amount));
        return TransactionView.from(transactions.save(BankTransaction.withdrawal(account, amount, actor, description)));
    }
    private TransactionView doTransfer(String actorEmail, String fromNumber, String toNumber, BigDecimal amount, String description, String idempotencyKey){
        User actor = requireUser(actorEmail);
        Long fromId = requireAccountId(fromNumber);
        Long toId = requireAccountId(toNumber);
        if(fromId.equals(toId)){
            throw badRequest("You cannot transfer money to the same account.");
        }
        Map<Long, Account> locked = lock(fromId, toId);
        Account from = locked.get(fromId);
        Account to = locked.get(toId);
        if(idempotencyKey!=null){
            Optional<BankTransaction> existing = transactions.findByInitiatedByIdAndIdempotencyKey(actor.getId(), idempotencyKey);
            if(existing.isPresent()){
                return TransactionView.from(existing.get());
            }
        }
        requireOwner(from, actor);
        if(from.getCurrency()!=to.getCurrency()){
            throw new ResponseStatusException(UNPROCESSABLE, "Transfers between accounts in different currencies are not supported");
        }
        FailedAttempt attempt = new FailedAttempt(TransactionType.TRANSFER, actor.getId(), fromId, toId, amount, description);
        requireActive(from, attempt);
        requireActive(to, attempt);
        requireEnoughMoney(from, amount, attempt);
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        BankTransaction transfer = BankTransaction.transaction(from, to, amount, actor, description);
        transfer.setIdempotencyKey(idempotencyKey);
        BankTransaction saved = transactions.save(transfer);
        log.info("Transfer id={} {} -> {}: {} {}, by {}", saved.getId(), from.getAccountNumber(), to.getAccountNumber(), amount, from.getCurrency(), actorEmail);
        return TransactionView.from(saved);
    }
    private TransactionView runWithFailureLog(Supplier<TransactionView> operation){
        try {
            return tx.execute(satus -> operation.get());
        } catch (TransactionRejectedException e) {
            logFailure(e);
            throw e;
        }
    }
    private void logFailure(TransactionRejectedException e){
        FailedAttempt f = e.getAttempt();
        try {
            failureTx.executeWithoutResult(status -> {
                User actor = users.getReferenceById(f.actorId());
                Account from = f.fromAccountId() == null ? null : accounts.getReferenceById(f.fromAccountId());
                Account to = f.toAccountId() == null ? null : accounts.getReferenceById(f.toAccountId());
                BankTransaction failed = switch(f.type()){
                    case DEPOSIT -> BankTransaction.deposit(to, f.amount(), actor, f.description());
                    case WITHDRAWAL -> BankTransaction.withdrawal(from, f.amount(), actor, f.description());
                    case TRANSFER -> BankTransaction.transaction(from, to, f.amount(), actor, f.description());
                };
                failed.markFailed(e.getCode());
                transactions.save(failed);
            });
        } catch (RuntimeException logError) {
            log.error("Failed to record the unsuccessful operation in the log", logError);
        }
    }
    private User requireUser(String email){
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
    private Long requireAccountId(String accountNumber){
        if(accountNumber==null||accountNumber.isBlank()){
            throw badRequest("Account number not specified");
        }
        return accounts.findIdByAccountNumber(accountNumber.trim().toUpperCase(Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found:"+accountNumber));
    }
    private void requireActive(Account account, FailedAttempt attempt){
        if(account.getStatus()!=AccountStatus.ACTIVE){
            throw new TransactionRejectedException("Account blocked: "+account.getAccountNumber(), "ACCOUNT_BLOCKED", attempt);
        }
    }
    private void requireEnoughMoney(Account account, BigDecimal amount, FailedAttempt attempt){
        if(account.getBalance().compareTo(amount)<0){
            throw new TransactionRejectedException("Insufficient funds in the account", "INSUFFICIENT_FUNDS", attempt);
        }
    }
    private void requireOwner(Account account, User actor){
        if(!account.getUser().getId().equals(actor.getId())){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The account belongs to another user");
        }
    }
    private Map<Long, Account> lock(Long... ids){
        Map<Long, Account> byId = new HashMap<>();
        for(Account account : accounts.findAllByIdForUpdate(List.of(ids))){
            byId.put(account.getId(), account);
        }
        for(Long id:ids){
            if(!byId.containsKey(id)){
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
            }
        }
        return byId;
    }
    private static BigDecimal normalizeAmount(BigDecimal amount){
        if(amount == null||amount.signum()<=0){
            throw badRequest("The amount must be greater than zero");
        }
        if(amount.stripTrailingZeros().scale()>2){
            throw badRequest("The amount may contain no more than two decimal places.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if(normalized.compareTo(MAX_AMOUNT)>0){
            throw badRequest("The amount is too large");
        }
        return normalized;
    }
    private static String normalizeKey(String idempotencyKey){
        if(idempotencyKey==null||idempotencyKey.isBlank()){
            return null;
        }
        String key = idempotencyKey.trim();
        if(key.length()>MAX_IDEMPOTENCY_KEY_LENGTH){
            throw badRequest("Idempotency-Key is longer "+MAX_IDEMPOTENCY_KEY_LENGTH+" characters");
        }
        return key;
    }
    private static ResponseStatusException badRequest(String message){
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

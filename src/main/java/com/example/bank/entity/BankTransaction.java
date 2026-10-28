package com.example.bank.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table; 
@Entity
@Table(name="transactions") 
public class BankTransaction {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="from_account_id", updatable=false)
    private Account fromAccount;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="to_account_id", updatable=false)
    private Account toAccount;

    @Column(nullable=false, precision=19, scale=2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, updatable=false, length=3)
    private Currency currency = Currency.UAH;

    @Enumerated(EnumType.STRING)
    @Column(name="transaction_type", nullable=false, updatable=false, length=20)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, updatable=false, length=20)
    private TransactionStatus status;

    @Column(name="failure_reason", updatable=false)
    private String failureReason;

    @Column(updatable=false)
    private String description;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="initiated_by", nullable=false, updatable=false)
    private User initiatedBy;

    @Column(name="idempotency_key", nullable=false, updatable=false, length=64)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private Instant creatAd;
    
    protected BankTransaction(){}

    public BankTransaction(Account from, Account to, BigDecimal amount, Currency currency, TransactionType type, User initiatedBy, String description, String idempotencyKey){
        this.fromAccount = from;
        this.toAccount = to;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.initiatedBy = initiatedBy;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.status = TransactionStatus.SUCCESS;
    }

    public static BankTransaction deposit(Account to, BigDecimal amount, User initiatedBy, String description, String idempotencyKey){
        requirePositive(amount);
        return new BankTransaction(null, to, amount, to.getCurrency(), TransactionType.DEPOSIT, initiatedBy, description, idempotencyKey);
    }
    public static BankTransaction withdrawal(Account from, BigDecimal amount, User initiatedBy, String description, String idempotencyKey){
        requirePositive(amount);
        return new BankTransaction(from, null, amount, from.getCurrency(), TransactionType.WITHDRAWAL, initiatedBy, description, idempotencyKey);
    }
    public static BankTransaction transaction(Account from, Account to, BigDecimal amount, Currency currency, TransactionType type, User initiatedBy, String description, String idempotencyKey){
        requirePositive(amount);
        if(from == to){
            throw new IllegalArgumentException("You cannot transfer money to the same account.");
        }
        if(from.getCurrency()!=to.getCurrency()){
            throw new IllegalArgumentException("Account currencies do not match");
        }
        return new BankTransaction(from, to, amount, from.getCurrency(), TransactionType.TRANSFER, initiatedBy, description, idempotencyKey);
    }
    public static void requirePositive(BigDecimal amount){
        if(amount==null||amount.signum() <=0){
            throw new IllegalArgumentException("The amount must be greater than zero");
        }
    }
    public void markFailed(String reason){
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
    }
    public Long getId(){
        return id;
    }
    public Account getFromAccount(){
        return fromAccount;
    }
    public Account getToAccount(){
        return toAccount;
    }
    public BigDecimal getAmount(){
        return amount;
    }
    public Currency getCurrency(){
        return currency;
    }
    public TransactionType getType(){
        return type;
    }
    public TransactionStatus getStatus(){
        return status;
    }
    public String getFailureReason(){
        return failureReason;
    }
    public String getDescription(){
        return description;
    }
    public User getInitiatedBy(){
        return initiatedBy;
    }
    public String getIdempotencyKey(){
        return idempotencyKey;
    }
    public Instant getCreatedAt(){
        return creatAd;
    }
}

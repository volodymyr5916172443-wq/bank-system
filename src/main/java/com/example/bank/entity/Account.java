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
import jakarta.persistence.Version;

@Entity
@Table(name="accounts")
public class Account {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name="account_number", nullable=false, unique=true, length=29)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=3)
    private Currency currency = Currency.UAH;

    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version 
    private Long version;

    @CreationTimestamp 
    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt;

    protected Account(){}


    public Account(User user, String accountNumber, Currency currency){
        this.user = user;
        this.accountNumber = accountNumber;
        this.currency = currency;
    }
    public Long getId(){
        return id;
    }
    public User getUser(){
        return user;
    }
    public String getAccountNumber(){
        return accountNumber;
    }
    public Currency getCurrency(){
        return currency;
    }
    public BigDecimal getBalance(){
        return balance;
    }
    public void setBalance(BigDecimal balance){
        this.balance = balance;
    }
    public AccountStatus getStatus(){
        return status;
    }
    public void setStatus(AccountStatus status){
        this.status = status;
    }
    public Long getVersion(){
        return version;
    }
    public Instant getCreatedAt(){
        return createdAt;
    }
}

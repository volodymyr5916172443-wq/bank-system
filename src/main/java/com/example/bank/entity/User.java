package com.example.bank.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name="users")
public class User {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=255)
    private String email;

    @Column(name="password_hash", nullable=false, length=255)
    private String passwordHash;

    @Column(name="full_name", nullable=false, length=255)
    private String fullName;

    @Column(name="created_at", nullable=false, updatable=false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name="user_role", nullable=false, length=20)
    private UserRole userRole = UserRole.CUSTOMER;

    protected User(){}
    public User(String email, String passwordHash,  String fullName){
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }
    @PrePersist
    protected void onCreate(){
        if(createdAt==null){
            createdAt = LocalDateTime.now();
        }
    }
    public Long getId(){
        return id;
    }
    public String getEmail(){
        return email;
    }
    public String getPasswordHash(){
        return passwordHash;
    }
    public String getFullName(){
        return fullName;
    }
    public LocalDateTime getCreatedAt(){
        return createdAt;
    }
    public UserRole getUserRole(){
        return userRole;
    }
    public void changeRole(UserRole userRole){
        this.userRole = userRole;
    }

}

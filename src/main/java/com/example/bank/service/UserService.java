package com.example.bank.service;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.bank.entity.User;
import com.example.bank.repository.UserRepository;

@Service 
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder){
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword, String fullname){
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if(users.existsByEmail(normalizedEmail)){
            throw emailTaken();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), fullname.trim());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
    }
    private static ResponseStatusException emailTaken(){
        return new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
    }
}

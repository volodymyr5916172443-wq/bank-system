package com.example.bank.config;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.UserRepository;


@Component 
public class AdminBootstrap implements ApplicationRunner{
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String fullName;
    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password, @Value("${app.admin.full-name:Administrator}") String fullName){
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args){
        if(email.isBlank()||password.isBlank()){
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = users.findByEmail(email);
        if(existing.isPresent()){
            if(existing.get().getUserRole()==UserRole.ADMIN){
                log.warn("Administrator not created: email admin@gmail.com is already taken by a regular user", normalizedEmail);
            }
            return;
        }
        User admin = new User(normalizedEmail, passwordEncoder.encode(password), fullName);
        admin.changeRole(UserRole.ADMIN);
        users.save(admin);
        log.info("The first administrator has been created: Administrator", normalizedEmail);
    } 

}

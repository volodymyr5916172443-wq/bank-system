package com.example.bank.service;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.bank.entity.User;
import com.example.bank.repository.UserRepository;
@Service 
public class CustomUserDetailsService implements UserDetailsService{
    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users){
        this.users = users;
    }
    @Override
    public UserDetails  loadUserByUsername(String email) throws UsernameNotFoundException{
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return org.springframework.security.core.userdetails.User.builder()
        .username(user.getEmail())
        .password(user.getPasswordHash())
        .roles(user.getUserRole().name())
        .build();
    }
}

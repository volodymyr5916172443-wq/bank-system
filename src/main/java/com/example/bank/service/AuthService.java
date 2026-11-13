package com.example.bank.service;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.example.bank.security.JwtService;

@Service 
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService){
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }
    public LoginResult login(String email, String password){
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email.trim().toLowerCase(Locale.ROOT), password));
        String token = jwtService.generateToken(authentication.getName());
        return new LoginResult(token, jwtService.getExpirationSeconds());
    }
    public record LoginResult(String accessToken, long expiresInSeconds){}
}

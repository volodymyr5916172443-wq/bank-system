package com.example.bank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.service.AuthService;
import com.example.bank.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@RestController 
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthService authService;
    public AuthController(UserService userService, AuthService authService){
        this.userService = userService;
        this.authService = authService;
    }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request){
        User user = userService.register(request.email(), request.password(), request.fullName());
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getUserRole());

    }
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody  LoginRequest request){
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        return new TokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
    }
    public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min=8, max=72) String password,
        @NotBlank @Size(max=255) String fullName
    ){}
    public record LoginRequest(@NotBlank String email, @NotBlank String password){}
    public record UserResponse(Long id, String email, String fullName, UserRole role){}
    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds){}
}

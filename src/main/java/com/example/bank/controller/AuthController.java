package com.example.bank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@RestController 
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService){
        this.userService = userService;
    }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request){
        User user = userService.register(request.email(), request.password(), request.fullName());
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getUserRole());

    }
    public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min=8, max=72) String password,
        @NotBlank @Size(max=255) String fullName
    ){}

    public record UserResponse(Long id, String email, String fullName, UserRole role){}
}

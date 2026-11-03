package com.example.bank.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.service.UserRoleService;

@RestController
@RequestMapping("/api/admin/users") 
public class AdminUserController {
    private final UserRoleService userRoleService;
    public AdminUserController(UserRoleService userRoleService){
        this.userRoleService = userRoleService;
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserRoleResponse changeRole(@PathVariable Long id,@RequestBody ChangeRoleRequest request, Authentication authentication){
        User update = userRoleService.changeRole(id, request.userRole(), authentication.getName());
        return new UserRoleResponse(update.getId(), update.getEmail(), update.getUserRole());
    }
    public record ChangeRoleRequest(UserRole userRole){}
    public record UserRoleResponse(Long id, String email, UserRole userRole){}
}

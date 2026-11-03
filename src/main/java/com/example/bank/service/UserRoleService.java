package com.example.bank.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.UserRepository;
@Service 
public class UserRoleService {
    private static final Logger log = LoggerFactory.getLogger(UserRoleService.class);
    private final UserRepository users;

    public UserRoleService(UserRepository users){
        this.users = users;
    }

    @Transactional
    public User changeRole(Long targetUserId, UserRole newRole, String actorEmail){
        if(newRole == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role not specified");
        }
        User target = users.findById(targetUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if(target.getEmail().equalsIgnoreCase(actorEmail)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One cannot change one's own role");
        }
        UserRole oldRole = target.getUserRole();
        if(oldRole != newRole){
            target.setUserRole(newRole);
            log.info("Role changed: user id={} {} -> {}, by {}", target.getId(), oldRole, newRole, actorEmail);
        }
        return target;
    } 
}

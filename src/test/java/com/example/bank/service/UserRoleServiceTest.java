package com.example.bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRoleServiceTest {

    @Autowired
    UserRoleService service;
    @Autowired
    UserRepository users;
    @Autowired
    EntityManager em;

    @Test
    void newUserIsCustomerByDefault() {
        User user = users.saveAndFlush(new User("new@test.com", "hash", "New User"));
        em.clear();

        assertThat(users.findById(user.getId()).orElseThrow().getUserRole())
                .isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void adminCanPromoteCustomerToAdmin() {
        User admin = users.saveAndFlush(new User("admin@test.com", "hash", "Admin"));
        User customer = users.saveAndFlush(new User("customer@test.com", "hash", "Customer"));

        service.changeRole(customer.getId(), UserRole.ADMIN, admin.getEmail());
        em.flush();
        em.clear(); 

        assertThat(users.findById(customer.getId()).orElseThrow().getUserRole())
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void adminCanDemoteAdminBackToCustomer() {
        User admin = users.saveAndFlush(new User("admin@test.com", "hash", "Admin"));
        User other = users.saveAndFlush(new User("other@test.com", "hash", "Other"));
        service.changeRole(other.getId(), UserRole.ADMIN, admin.getEmail());

        service.changeRole(other.getId(), UserRole.CUSTOMER, admin.getEmail());
        em.flush();
        em.clear();

        assertThat(users.findById(other.getId()).orElseThrow().getUserRole())
                .isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void cannotChangeOwnRole() {
        User admin = users.saveAndFlush(new User("admin@test.com", "hash", "Admin"));

        assertThatThrownBy(() -> service.changeRole(admin.getId(), UserRole.CUSTOMER, "ADMIN@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("one's own role");
    }

    @Test
    void unknownUserIsRejected() {
        assertThatThrownBy(() -> service.changeRole(999_999_999L, UserRole.ADMIN, "admin@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void nullRoleIsRejected() {
        User user = users.saveAndFlush(new User("u@test.com", "hash", "U"));

        assertThatThrownBy(() -> service.changeRole(user.getId(), null, "admin@test.com"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
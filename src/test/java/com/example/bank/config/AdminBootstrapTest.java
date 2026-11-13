package com.example.bank.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.UserRepository;

@SpringBootTest(properties = {
        "app.admin.email=Boss@Test.com",
        "app.admin.password=admin-pass-123"
})
@ActiveProfiles("test")
class AdminBootstrapTest {

    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users where email = 'boss@test.com'");
    }

    @Test
    void createsAdminOnStartup() {
        User admin = users.findByEmail("boss@test.com").orElseThrow();

        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN);
        assertThat(passwordEncoder.matches("admin-pass-123", admin.getPasswordHash())).isTrue();
    }
}
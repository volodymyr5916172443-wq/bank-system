package com.example.bank.controller;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.bank.entity.Account;
import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistrationAndAccountFlowTest {

    private static final String PASSWORD = "password123";

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository users;
    @Autowired
    AccountRepository accounts;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }


    @Test
    void registersCustomerWithNormalizedEmail() throws Exception {
        register("Anna@Test.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("anna@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        User saved = users.findByEmail("anna@test.com").orElseThrow();

        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(saved.getUserRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void roleCannotBeChosenDuringRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hacker@test.com","password":"password123",
                                 "fullName":"Hacker","role":"ADMIN","userRole":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        register("ANNA@test.com", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"123","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }


    @Test
    void accountEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"UAH\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        mockMvc.perform(get("/api/accounts").with(httpBasic("anna@test.com", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUseAdminEndpoint() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/users/1/role")
                        .with(httpBasic("anna@test.com", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }


    @Test
    void authenticatedUserCreatesAccount() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts")
                        .with(httpBasic("anna@test.com", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("UA\\d{27}")));

        List<Account> saved = accounts.findByUserEmailOrderByIdAsc("anna@test.com");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void userSeesOnlyOwnAccounts() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());
        register("boris@test.com", PASSWORD).andExpect(status().isCreated());

        createAccount("anna@test.com", "UAH");
        createAccount("anna@test.com", "EUR");
        createAccount("boris@test.com", "UAH");

        mockMvc.perform(get("/api/accounts").with(httpBasic("anna@test.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/accounts").with(httpBasic("boris@test.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void unsupportedCurrencyIsRejected() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts")
                        .with(httpBasic("anna@test.com", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"GBP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingCurrencyIsRejected() throws Exception {
        register("anna@test.com", PASSWORD).andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts")
                        .with(httpBasic("anna@test.com", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }


    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"fullName\":\"Test User\"}"));
    }

    private void createAccount(String email, String currency) throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .with(httpBasic(email, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated());
    }
}
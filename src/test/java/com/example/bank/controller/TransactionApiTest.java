package com.example.bank.controller;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.bank.entity.Account;
import com.example.bank.entity.Currency;
import com.example.bank.entity.User;
import com.example.bank.entity.UserRole;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.service.AccountNumberGenerator;
import com.jayway.jsonpath.JsonPath;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionApiTest {

    private static final String PASSWORD = "password123";

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository users;
    @Autowired
    AccountRepository accounts;
    @Autowired
    PasswordEncoder passwordEncoder;

    MockMvc mockMvc;
    Account annaAccount;
    Account borisAccount;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        saveUser("admin@test.com", UserRole.ADMIN);
        User anna = saveUser("anna@test.com", UserRole.CUSTOMER);
        User boris = saveUser("boris@test.com", UserRole.CUSTOMER);
        annaAccount = saveAccount(anna, "1000.00");
        borisAccount = saveAccount(boris, "0");
    }

    @Test
    void transactionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("100.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotDeposit() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .with(bearer("anna@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson("100.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDepositsMoney() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .with(bearer("admin@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson("250.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.toAccountNumber").value(annaAccount.getAccountNumber()));

        assertThat(balanceOf(annaAccount)).isEqualByComparingTo("1250.00");
    }

    @Test
    void customerTransfersMoneyAndRetryWithSameKeyDoesNotDuplicate() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/transactions/transfer")
                        .with(bearer("anna@test.com"))
                        .header("IdempotencyKey", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();
        Integer firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/transactions/transfer")
                        .with(bearer("anna@test.com"))
                        .header("IdempotencyKey", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(balanceOf(annaAccount)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(borisAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void negativeAmountIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .with(bearer("anna@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("-5.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void historyIsVisibleToOwnerOnly() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .with(bearer("admin@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson("10.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(annaAccount.getId()))
                        .with(bearer("anna@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"));

        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(annaAccount.getId()))
                        .with(bearer("boris@test.com")))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor bearer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private String depositJson(String amount) {
        return """
                {"accountNumber":"%s","amount":%s,"description":"cash in"}
                """.formatted(annaAccount.getAccountNumber(), amount);
    }

    private String transferJson(String amount) {
        return """
                {"fromAccountNumber":"%s","toAccountNumber":"%s","amount":%s,"description":"rent"}
                """.formatted(annaAccount.getAccountNumber(), borisAccount.getAccountNumber(), amount);
    }

    private User saveUser(String email, UserRole role) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test User");
        user.changeRole(role);
        return users.saveAndFlush(user);
    }

    private Account saveAccount(User owner, String balance) {
        Account account = new Account(owner, AccountNumberGenerator.generate(), Currency.UAH);
        account.setBalance(new BigDecimal(balance));
        return accounts.saveAndFlush(account);
    }

    private BigDecimal balanceOf(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }
}
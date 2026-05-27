package com.autobookkeeper.api;

import com.autobookkeeper.domain.ProcessingStatus;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.domain.TransactionType;
import com.autobookkeeper.repository.TransactionRepository;
import com.autobookkeeper.user.AppUser;
import com.autobookkeeper.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "autobookkeeper.api-token=test-token",
        "autobookkeeper.invite-code=join-test",
        "spring.datasource.url=jdbc:h2:mem:auth-controller-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void registersUserWithValidInviteCodeAndReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret123\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("friend1"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        AppUser user = appUserRepository.findByUsername("friend1").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getOwnerKey()).startsWith("user_");
        assertThat(user.getApiToken()).isNotBlank();
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret123\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret456\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsWrongInviteCode() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret123\",\"inviteCode\":\"wrong\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsInWithCorrectPasswordAndReturnsExistingToken() throws Exception {
        String token = mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret123\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AppUser user = appUserRepository.findByUsername("friend1").orElseThrow();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("friend1"))
                .andExpect(jsonPath("$.token").value(user.getApiToken()));

        assertThat(token).contains(user.getApiToken());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"secret123\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"friend1\",\"password\":\"wrongpw\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void migratesLegacyTransactionsToCurrentRegisteredUserWithLegacyToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"secret123\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isOk());
        AppUser owner = appUserRepository.findByUsername("owner").orElseThrow();
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.00"),
                "Legacy Null",
                TransactionType.EXPENSE,
                "餐饮",
                "raw",
                "{}",
                0.9,
                ProcessingStatus.PROCESSED,
                "legacy",
                Instant.parse("2026-05-01T00:00:00Z")
        ));
        Transaction defaultTransaction = new Transaction(
                LocalDate.of(2026, 5, 2),
                new BigDecimal("20.00"),
                "Legacy Default",
                TransactionType.EXPENSE,
                "购物",
                "raw",
                "{}",
                0.9,
                ProcessingStatus.PROCESSED,
                "legacy",
                Instant.parse("2026-05-02T00:00:00Z")
        );
        defaultTransaction.assignOwner("default");
        transactionRepository.save(defaultTransaction);

        mockMvc.perform(post("/api/auth/migrate-legacy")
                        .header("X-API-Token", owner.getApiToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"legacyToken\":\"test-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.migratedCount").value(2));

        assertThat(transactionRepository.findAll()).allMatch(transaction -> owner.getOwnerKey().equals(transaction.getOwnerKey()));
    }

    @Test
    void rejectsLegacyMigrationWithWrongLegacyToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"secret123\",\"inviteCode\":\"join-test\"}"))
                .andExpect(status().isOk());
        AppUser owner = appUserRepository.findByUsername("owner").orElseThrow();

        mockMvc.perform(post("/api/auth/migrate-legacy")
                        .header("X-API-Token", owner.getApiToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"legacyToken\":\"wrong-token\"}"))
                .andExpect(status().isForbidden());
    }
}

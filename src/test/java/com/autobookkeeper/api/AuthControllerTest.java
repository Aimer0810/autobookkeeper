package com.autobookkeeper.api;

import com.autobookkeeper.user.AppUser;
import com.autobookkeeper.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach
    void cleanDatabase() {
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
}

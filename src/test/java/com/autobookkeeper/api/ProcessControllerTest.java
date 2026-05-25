package com.autobookkeeper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "autobookkeeper.api-token=test-token",
        "spring.datasource.url=jdbc:h2:mem:process-controller-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
class ProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingApiTokenWhenConfigured() throws Exception {
        mockMvc.perform(post("/api/process")
                        .contentType(APPLICATION_JSON)
                        .content("{\"imageBase64\":\"" + Base64.getEncoder().encodeToString("test".getBytes()) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidBase64() throws Exception {
        mockMvc.perform(post("/api/process")
                        .header("X-API-Token", "test-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"imageBase64\":\"not-base64!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processesValidBase64Image() throws Exception {
        String base64 = Base64.getEncoder().encodeToString("fake-image".getBytes());

        mockMvc.perform(post("/api/process")
                        .header("X-API-Token", "test-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"imageBase64\":\"" + base64 + "\",\"source\":\"ios-shortcuts\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.needsReview").value(true));
    }

    @Test
    void rejectsTransactionListWithoutApiTokenWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsTransactionListWithApiTokenWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk());
    }
}

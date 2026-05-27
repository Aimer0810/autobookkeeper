package com.autobookkeeper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=local",
        "autobookkeeper.version=0.1.0-test",
        "autobookkeeper.ai.api-key=test-key",
        "autobookkeeper.ai.endpoint=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        "autobookkeeper.ai.model=qwen3.6-flash",
        "autobookkeeper.ai.timeout-ms=30000",
        "spring.datasource.url=jdbc:h2:mem:health-controller-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOperationalMetadata() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").value("0.1.0-test"))
                .andExpect(jsonPath("$.profiles[0]").value("local"))
                .andExpect(jsonPath("$.ai.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.ai.endpoint").value("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"))
                .andExpect(jsonPath("$.ai.model").value("qwen3.6-flash"))
                .andExpect(jsonPath("$.ai.timeoutMs").value(30000));
    }
}

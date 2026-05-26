package com.autobookkeeper.api;

import com.autobookkeeper.domain.ProcessingStatus;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.repository.TransactionRepository;
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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "autobookkeeper.api-token=test-token",
        "spring.datasource.url=jdbc:h2:mem:transaction-controller-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
    }

    @Test
    void reviewsTransactionAndMarksItProcessed() throws Exception {
        Transaction transaction = transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 26),
                new BigDecimal("19.90"),
                "待确认商户",
                "待分类",
                "raw text",
                "{}",
                0.45,
                ProcessingStatus.NEEDS_REVIEW,
                "ios-shortcuts",
                Instant.parse("2026-05-26T12:00:00Z")
        ));

        mockMvc.perform(patch("/api/transactions/" + transaction.getId())
                        .header("X-API-Token", "test-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionDate": "2026-05-25",
                                  "amount": 18.50,
                                  "merchant": "星巴克",
                                  "category": "餐饮",
                                  "status": "PROCESSED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionDate").value("2026-05-25"))
                .andExpect(jsonPath("$.amount").value(18.50))
                .andExpect(jsonPath("$.merchant").value("星巴克"))
                .andExpect(jsonPath("$.category").value("餐饮"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }
}

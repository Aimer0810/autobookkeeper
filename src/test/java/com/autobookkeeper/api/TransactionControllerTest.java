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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "autobookkeeper.api-token=test-token",
        "autobookkeeper.user-tokens=alice:alice-token,bob:bob-token",
        "spring.datasource.url=jdbc:h2:mem:transaction-controller-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void reviewsTransactionAndMarksItProcessed() throws Exception {
        Transaction transaction = transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 26),
                new BigDecimal("19.90"),
                "待确认商户",
                TransactionType.EXPENSE,
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
                                  "type": "支出",
                                  "category": "餐饮",
                                  "status": "PROCESSED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionDate").value("2026-05-25"))
                .andExpect(jsonPath("$.amount").value(18.50))
                .andExpect(jsonPath("$.merchant").value("星巴克"))
                .andExpect(jsonPath("$.type").value("支出"))
                .andExpect(jsonPath("$.category").value("餐饮"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void listsTransactionsForSelectedMonth() throws Exception {
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.00"),
                "早餐店",
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-01T12:00:00Z")
        ));
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 20),
                new BigDecimal("25.50"),
                "地铁",
                "交通",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-20T12:00:00Z")
        ));
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 4, 30),
                new BigDecimal("99.00"),
                "上月账目",
                "购物",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-04-30T12:00:00Z")
        ));

        mockMvc.perform(get("/api/transactions?month=2026-05&page=0&size=10")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfElements").value(2))
                .andExpect(jsonPath("$.content[0].merchant").value("地铁"))
                .andExpect(jsonPath("$.content[0].type").value("支出"))
                .andExpect(jsonPath("$.content[1].merchant").value("早餐店"));
    }

    @Test
    void databaseUserTokensOnlySeeTheirOwnTransactions() throws Exception {
        AppUser alice = appUserRepository.save(new AppUser(
                "alice",
                "$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "db-alice",
                "db-alice-token",
                Instant.parse("2026-05-01T00:00:00Z")
        ));
        AppUser bob = appUserRepository.save(new AppUser(
                "bob",
                "$2a$10$bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "db-bob",
                "db-bob-token",
                Instant.parse("2026-05-01T00:00:00Z")
        ));
        Transaction aliceTransaction = new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.00"),
                "Alice Cafe",
                TransactionType.EXPENSE,
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-01T12:00:00Z")
        );
        aliceTransaction.assignOwner(alice.getOwnerKey());
        transactionRepository.save(aliceTransaction);
        Transaction bobTransaction = new Transaction(
                LocalDate.of(2026, 5, 2),
                new BigDecimal("20.00"),
                "Bob Market",
                TransactionType.EXPENSE,
                "购物",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-02T12:00:00Z")
        );
        bobTransaction.assignOwner(bob.getOwnerKey());
        transactionRepository.save(bobTransaction);

        mockMvc.perform(get("/api/transactions?month=2026-05&page=0&size=10")
                        .header("X-API-Token", "db-alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.content[0].merchant").value("Alice Cafe"));

        mockMvc.perform(get("/api/transactions?month=2026-05&page=0&size=10")
                        .header("X-API-Token", "db-bob-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.content[0].merchant").value("Bob Market"));
    }

    @Test
    void deletesTransaction() throws Exception {
        Transaction transaction = transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 26),
                new BigDecimal("19.90"),
                "误识别商户",
                TransactionType.EXPENSE,
                "待分类",
                "raw text",
                "{}",
                0.45,
                ProcessingStatus.NEEDS_REVIEW,
                "ios-shortcuts",
                Instant.parse("2026-05-26T12:00:00Z")
        ));

        mockMvc.perform(delete("/api/transactions/" + transaction.getId())
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions/" + transaction.getId())
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void summarizesMonthlyIncomeExpenseAndBalanceByCategory() throws Exception {
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.00"),
                "早餐店",
                TransactionType.EXPENSE,
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-01T12:00:00Z")
        ));
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 2),
                new BigDecimal("25.50"),
                "地铁",
                TransactionType.EXPENSE,
                "交通",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-02T12:00:00Z")
        ));
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 3),
                new BigDecimal("5000.00"),
                "公司",
                TransactionType.INCOME,
                "工资",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-03T12:00:00Z")
        ));
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 4),
                new BigDecimal("100.00"),
                "退款",
                TransactionType.INCOME,
                "退款",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-04T12:00:00Z")
        ));
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 4, 30),
                new BigDecimal("99.00"),
                "上月账目",
                TransactionType.EXPENSE,
                "购物",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-04-30T12:00:00Z")
        ));

        mockMvc.perform(get("/api/transactions/summary?month=2026-05")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-05"))
                .andExpect(jsonPath("$.incomeTotal").value(5100.00))
                .andExpect(jsonPath("$.expenseTotal").value(35.50))
                .andExpect(jsonPath("$.balance").value(5064.50))
                .andExpect(jsonPath("$.incomeCategorySummaries[0].category").value("工资"))
                .andExpect(jsonPath("$.incomeCategorySummaries[0].amount").value(5000.00))
                .andExpect(jsonPath("$.incomeCategorySummaries[1].category").value("退款"))
                .andExpect(jsonPath("$.incomeCategorySummaries[1].amount").value(100.00))
                .andExpect(jsonPath("$.expenseCategorySummaries[0].category").value("餐饮"))
                .andExpect(jsonPath("$.expenseCategorySummaries[0].amount").value(10.00))
                .andExpect(jsonPath("$.expenseCategorySummaries[1].category").value("交通"))
                .andExpect(jsonPath("$.expenseCategorySummaries[1].amount").value(25.50));
    }

    @Test
    void rejectsInvalidSummaryMonth() throws Exception {
        mockMvc.perform(get("/api/transactions/summary?month=bad")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isolatesTransactionsByUserToken() throws Exception {
        Transaction aliceTransaction = new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.00"),
                "Alice Store",
                TransactionType.EXPENSE,
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-01T12:00:00Z")
        );
        aliceTransaction.assignOwner("alice");
        transactionRepository.save(aliceTransaction);
        Transaction bobTransaction = new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("20.00"),
                "Bob Store",
                TransactionType.EXPENSE,
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-01T13:00:00Z")
        );
        bobTransaction.assignOwner("bob");
        transactionRepository.save(bobTransaction);

        mockMvc.perform(get("/api/transactions?month=2026-05")
                        .header("X-API-Token", "alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].merchant").value("Alice Store"));

        mockMvc.perform(get("/api/transactions/summary?month=2026-05")
                        .header("X-API-Token", "bob-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseTotal").value(20.00));
    }

    @Test
    void preventsCrossUserReadUpdateAndDelete() throws Exception {
        Transaction aliceTransaction = new Transaction(
                LocalDate.of(2026, 5, 26),
                new BigDecimal("19.90"),
                "Alice Merchant",
                TransactionType.EXPENSE,
                "待分类",
                "raw text",
                "{}",
                0.45,
                ProcessingStatus.NEEDS_REVIEW,
                "ios-shortcuts",
                Instant.parse("2026-05-26T12:00:00Z")
        );
        aliceTransaction.assignOwner("alice");
        Transaction saved = transactionRepository.save(aliceTransaction);

        mockMvc.perform(get("/api/transactions/" + saved.getId())
                        .header("X-API-Token", "bob-token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/transactions/" + saved.getId())
                        .header("X-API-Token", "bob-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "merchant": "Bob Merchant",
                                  "status": "PROCESSED"
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/transactions/" + saved.getId())
                        .header("X-API-Token", "bob-token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/transactions/" + saved.getId())
                        .header("X-API-Token", "alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Alice Merchant"));
    }

    @Test
    void defaultTokenCanReadLegacyTransactionsWithoutOwnerKey() throws Exception {
        Transaction legacyTransaction = transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 2),
                new BigDecimal("33.00"),
                "Legacy Store",
                TransactionType.EXPENSE,
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-02T12:00:00Z")
        ));
        jdbcTemplate.update("update transactions set owner_key = null where id = ?", legacyTransaction.getId());

        mockMvc.perform(get("/api/transactions?month=2026-05")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].merchant").value("Legacy Store"));

        mockMvc.perform(get("/api/transactions/summary?month=2026-05")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseTotal").value(33.00));

        mockMvc.perform(get("/api/transactions/" + legacyTransaction.getId())
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Legacy Store"));
    }

    @Test
    void treatsLegacyTransactionWithoutTypeAsExpense() throws Exception {
        transactionRepository.save(new Transaction(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("12.00"),
                "旧数据商户",
                null,
                "餐饮",
                "raw text",
                "{}",
                0.95,
                ProcessingStatus.PROCESSED,
                "ios-shortcuts",
                Instant.parse("2026-05-01T12:00:00Z")
        ));

        mockMvc.perform(get("/api/transactions/summary?month=2026-05")
                        .header("X-API-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeTotal").value(0))
                .andExpect(jsonPath("$.expenseTotal").value(12.00))
                .andExpect(jsonPath("$.balance").value(-12.00));
    }
}

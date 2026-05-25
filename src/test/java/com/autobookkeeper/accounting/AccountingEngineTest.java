package com.autobookkeeper.accounting;

import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.ProcessingStatus;
import com.autobookkeeper.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingEngineTest {

    private final AccountingEngine engine = new AccountingEngine(new CategoryRuleLoader(Map.of(
            "餐饮", List.of("麦当劳", "瑞幸"),
            "交通", List.of("滴滴")
    )));

    @Test
    void categorizesMerchantByKeywordRule() {
        Bill bill = new Bill(LocalDate.of(2026, 5, 25), new BigDecimal("29.90"), "麦当劳北京店", "购物", "raw", "{}", 0.95, false);

        Transaction transaction = engine.createTransaction(bill, "ios-shortcuts");

        assertThat(transaction.getCategory()).isEqualTo("餐饮");
        assertThat(transaction.getStatus()).isEqualTo(ProcessingStatus.PROCESSED);
    }

    @Test
    void usesAiCategoryWhenNoRuleMatches() {
        Bill bill = new Bill(LocalDate.of(2026, 5, 25), new BigDecimal("18.00"), "未知商家", "娱乐", "raw", "{}", 0.9, false);

        Transaction transaction = engine.createTransaction(bill, "ios-shortcuts");

        assertThat(transaction.getCategory()).isEqualTo("娱乐");
    }

    @Test
    void marksLowConfidenceBillForReview() {
        Bill bill = new Bill(LocalDate.of(2026, 5, 25), new BigDecimal("18.00"), "未知商家", "", "raw", "{}", 0.5, false);

        Transaction transaction = engine.createTransaction(bill, "ios-shortcuts");

        assertThat(transaction.getCategory()).isEqualTo("未分类");
        assertThat(transaction.getStatus()).isEqualTo(ProcessingStatus.NEEDS_REVIEW);
    }
}

package com.autobookkeeper.accounting;

import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.ProcessingStatus;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.domain.TransactionType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

@Service
public class AccountingEngine {

    private static final double REVIEW_CONFIDENCE_THRESHOLD = 0.75;

    private final CategoryRuleLoader categoryRuleLoader;

    public AccountingEngine(CategoryRuleLoader categoryRuleLoader) {
        this.categoryRuleLoader = categoryRuleLoader;
    }

    public Transaction createTransaction(Bill bill, String source) {
        String category = resolveCategory(bill);
        TransactionType type = bill.type() == null ? TransactionType.inferFromCategory(category) : bill.type();
        ProcessingStatus status = bill.needsReview() || bill.confidence() < REVIEW_CONFIDENCE_THRESHOLD
                ? ProcessingStatus.NEEDS_REVIEW
                : ProcessingStatus.PROCESSED;
        return new Transaction(
                bill.date() == null ? LocalDate.now() : bill.date(),
                bill.amount(),
                blankToDefault(bill.merchant(), "未知商家"),
                type,
                category,
                bill.rawText(),
                bill.structuredJson(),
                bill.confidence(),
                status,
                blankToDefault(source, "unknown"),
                Instant.now()
        );
    }

    private String resolveCategory(Bill bill) {
        String merchant = blankToDefault(bill.merchant(), "");
        for (var entry : categoryRuleLoader.rules().entrySet()) {
            for (String keyword : entry.getValue()) {
                if (!keyword.isBlank() && merchant.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return blankToDefault(bill.category(), "未分类");
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

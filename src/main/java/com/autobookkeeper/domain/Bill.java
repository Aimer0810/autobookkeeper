package com.autobookkeeper.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Bill(
        LocalDate date,
        BigDecimal amount,
        String merchant,
        TransactionType type,
        String category,
        String rawText,
        String structuredJson,
        double confidence,
        boolean needsReview
) {
    /** 简化构造函数：根据 category 自动推断交易类型（收入/支出） */
    public Bill(LocalDate date, BigDecimal amount, String merchant, String category, String rawText, String structuredJson, double confidence, boolean needsReview) {
        this(date, amount, merchant, TransactionType.inferFromCategory(category), category, rawText, structuredJson, confidence, needsReview);
    }
}

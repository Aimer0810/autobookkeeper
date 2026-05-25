package com.autobookkeeper.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Bill(
        LocalDate date,
        BigDecimal amount,
        String merchant,
        String category,
        String rawText,
        String structuredJson,
        double confidence,
        boolean needsReview
) {
}

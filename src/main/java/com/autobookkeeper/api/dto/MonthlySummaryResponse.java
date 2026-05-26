package com.autobookkeeper.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlySummaryResponse(
        String month,
        BigDecimal totalAmount,
        List<CategorySummary> categorySummaries
) {
    public record CategorySummary(String category, BigDecimal amount) {
    }
}

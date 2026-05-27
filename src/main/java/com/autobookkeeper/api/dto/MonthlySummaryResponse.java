package com.autobookkeeper.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlySummaryResponse(
        String month,
        BigDecimal incomeTotal,
        BigDecimal expenseTotal,
        BigDecimal balance,
        List<CategorySummary> incomeCategorySummaries,
        List<CategorySummary> expenseCategorySummaries
) {
    public record CategorySummary(String category, BigDecimal amount) {
    }
}

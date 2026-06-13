package com.autobookkeeper.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record YearlyReportResponse(
        int year,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        List<MonthData> months,
        List<CategoryData> incomeByCategory,
        List<CategoryData> expenseByCategory,
        int transactionCount,
        BigDecimal avgMonthlyExpense
) {
    public record MonthData(String month, BigDecimal income, BigDecimal expense) {}
    public record CategoryData(String category, BigDecimal amount, int count) {}
}

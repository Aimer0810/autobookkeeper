package com.autobookkeeper.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record TrendResponse(List<MonthData> months) {
    public record MonthData(String month, BigDecimal income, BigDecimal expense) {
    }
}

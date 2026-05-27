package com.autobookkeeper.domain;

import java.util.Set;

public enum TransactionType {
    INCOME("收入"),
    EXPENSE("支出");

    private static final Set<String> INCOME_CATEGORIES = Set.of("工资", "奖金", "报销", "退款", "转账收入", "收入", "其他收入");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TransactionType fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return EXPENSE;
        }
        if ("收入".equals(value) || "INCOME".equalsIgnoreCase(value)) {
            return INCOME;
        }
        return EXPENSE;
    }

    public static TransactionType inferFromCategory(String category) {
        return INCOME_CATEGORIES.contains(category) ? INCOME : EXPENSE;
    }
}

package com.autobookkeeper.ai;

import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface AIService {

    Bill extractBillFromImage(byte[] imageData);

    static Bill placeholderBill(String rawText, String providerJson) {
        return new Bill(
                LocalDate.now(),
                BigDecimal.ZERO,
                "待复核商家",
                TransactionType.EXPENSE,
                "未分类",
                rawText,
                providerJson,
                0.2,
                true
        );
    }
}

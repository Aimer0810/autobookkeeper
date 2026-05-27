package com.autobookkeeper.api.dto;

import com.autobookkeeper.domain.ProcessingStatus;
import com.autobookkeeper.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        LocalDate transactionDate,
        BigDecimal amount,
        String merchant,
        String type,
        String category,
        ProcessingStatus status,
        double confidence,
        String rawText,
        String structuredJson,
        String source,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getType().label(),
                transaction.getCategory(),
                transaction.getStatus(),
                transaction.getConfidence(),
                transaction.getRawText(),
                transaction.getStructuredJson(),
                transaction.getSource(),
                transaction.getCreatedAt()
        );
    }
}

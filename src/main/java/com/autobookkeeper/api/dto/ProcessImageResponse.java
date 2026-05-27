package com.autobookkeeper.api.dto;

import com.autobookkeeper.domain.ProcessingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcessImageResponse(
        Long transactionId,
        LocalDate date,
        ProcessingStatus status,
        String merchant,
        BigDecimal amount,
        String category,
        double confidence,
        boolean needsReview,
        String reviewReason
) {
}

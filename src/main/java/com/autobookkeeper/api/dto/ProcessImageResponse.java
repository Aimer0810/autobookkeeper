package com.autobookkeeper.api.dto;

import com.autobookkeeper.domain.ProcessingStatus;

import java.math.BigDecimal;

public record ProcessImageResponse(
        Long transactionId,
        ProcessingStatus status,
        String merchant,
        BigDecimal amount,
        String category,
        double confidence,
        boolean needsReview
) {
}

package com.autobookkeeper.api.dto;

import com.autobookkeeper.domain.ProcessingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
        LocalDate transactionDate,
        BigDecimal amount,
        String merchant,
        String type,
        String category,
        ProcessingStatus status
) {
}

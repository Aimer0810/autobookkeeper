package com.autobookkeeper.api;

import com.autobookkeeper.accounting.AccountingEngine;
import com.autobookkeeper.ai.AIService;
import com.autobookkeeper.api.dto.ProcessImageRequest;
import com.autobookkeeper.api.dto.ProcessImageResponse;
import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

@RestController
@RequestMapping("/api")
public class ProcessController {

    private final AIService aiService;
    private final AccountingEngine accountingEngine;
    private final TransactionRepository transactionRepository;

    public ProcessController(AIService aiService, AccountingEngine accountingEngine, TransactionRepository transactionRepository) {
        this.aiService = aiService;
        this.accountingEngine = accountingEngine;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/process")
    public ProcessImageResponse process(@Valid @RequestBody ProcessImageRequest request) {
        byte[] imageData;
        try {
            imageData = Base64.getDecoder().decode(normalizeBase64(request.imageBase64()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageBase64 must be valid Base64");
        }
        Bill bill = aiService.extractBillFromImage(imageData);
        Transaction transaction = transactionRepository.save(accountingEngine.createTransaction(bill, request.source()));
        return new ProcessImageResponse(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getConfidence(),
                transaction.getStatus().name().equals("NEEDS_REVIEW")
        );
    }

    private String normalizeBase64(String imageBase64) {
        String value = imageBase64;
        int commaIndex = value.indexOf(',');
        if (value.startsWith("data:") && commaIndex >= 0) {
            value = value.substring(commaIndex + 1);
        }
        return value.replaceAll("\\s", "");
    }
}

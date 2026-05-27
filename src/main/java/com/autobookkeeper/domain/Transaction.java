package com.autobookkeeper.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate transactionDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private TransactionType type = TransactionType.EXPENSE;

    @Column(nullable = false)
    private String category;

    @Column(length = 8000)
    private String rawText;

    @Column(length = 8000)
    private String structuredJson;

    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(LocalDate transactionDate, BigDecimal amount, String merchant, String category, String rawText, String structuredJson, double confidence, ProcessingStatus status, String source, Instant createdAt) {
        this(transactionDate, amount, merchant, TransactionType.inferFromCategory(category), category, rawText, structuredJson, confidence, status, source, createdAt);
    }

    public Transaction(LocalDate transactionDate, BigDecimal amount, String merchant, TransactionType type, String category, String rawText, String structuredJson, double confidence, ProcessingStatus status, String source, Instant createdAt) {
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.merchant = merchant;
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.category = category;
        this.rawText = rawText;
        this.structuredJson = structuredJson;
        this.confidence = confidence;
        this.status = status;
        this.source = source;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public TransactionType getType() {
        return type == null ? TransactionType.EXPENSE : type;
    }

    public String getCategory() {
        return category;
    }

    public String getRawText() {
        return rawText;
    }

    public String getStructuredJson() {
        return structuredJson;
    }

    public double getConfidence() {
        return confidence;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(LocalDate transactionDate, BigDecimal amount, String merchant, String category, ProcessingStatus status) {
        update(transactionDate, amount, merchant, null, category, status);
    }

    public void update(LocalDate transactionDate, BigDecimal amount, String merchant, TransactionType type, String category, ProcessingStatus status) {
        if (transactionDate != null) {
            this.transactionDate = transactionDate;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (merchant != null && !merchant.isBlank()) {
            this.merchant = merchant;
        }
        if (type != null) {
            this.type = type;
        }
        if (category != null && !category.isBlank()) {
            this.category = category;
        }
        if (status != null) {
            this.status = status;
            if (status == ProcessingStatus.PROCESSED) {
                this.confidence = Math.max(this.confidence, 1.0);
            }
        }
    }
}

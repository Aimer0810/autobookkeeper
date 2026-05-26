package com.autobookkeeper.api;

import com.autobookkeeper.api.dto.MonthlySummaryResponse;
import com.autobookkeeper.api.dto.TransactionResponse;
import com.autobookkeeper.api.dto.UpdateTransactionRequest;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;

    public TransactionController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public Page<TransactionResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return transactionRepository.findAllByOrderByTransactionDateDescCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)))
                .map(TransactionResponse::from);
    }

    @GetMapping("/summary")
    public MonthlySummaryResponse summary(@RequestParam String month) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "month must use yyyy-MM format");
        }
        List<Transaction> transactions = transactionRepository.findAllByTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                yearMonth.atDay(1),
                yearMonth.plusMonths(1).atDay(1)
        );
        BigDecimal totalAmount = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        transactions.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate).thenComparing(Transaction::getCreatedAt))
                .forEach(transaction -> byCategory.merge(transaction.getCategory(), transaction.getAmount(), BigDecimal::add));
        List<MonthlySummaryResponse.CategorySummary> categorySummaries = byCategory.entrySet().stream()
                .map(entry -> new MonthlySummaryResponse.CategorySummary(entry.getKey(), entry.getValue()))
                .toList();
        return new MonthlySummaryResponse(month, totalAmount, categorySummaries);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
    }

    @PatchMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @RequestBody UpdateTransactionRequest request) {
        return transactionRepository.findById(id)
                .map(transaction -> {
                    transaction.update(request.transactionDate(), request.amount(), request.merchant(), request.category(), request.status());
                    return TransactionResponse.from(transactionRepository.save(transaction));
                })
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Transaction not found");
        }
        transactionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

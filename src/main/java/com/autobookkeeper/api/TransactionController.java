package com.autobookkeeper.api;

import com.autobookkeeper.api.dto.MonthlySummaryResponse;
import com.autobookkeeper.api.dto.TransactionResponse;
import com.autobookkeeper.api.dto.UpdateTransactionRequest;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.domain.TransactionType;
import com.autobookkeeper.repository.TransactionRepository;
import com.autobookkeeper.security.ApiTokenFilter;
import com.autobookkeeper.security.AuthenticatedUser;
import com.autobookkeeper.security.UserTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Optional;

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
    public Page<TransactionResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String month,
                                          HttpServletRequest request) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        String ownerKey = authenticatedUser(request).ownerKey();
        if (month != null && !month.isBlank()) {
            YearMonth yearMonth = parseMonth(month);
            return findPage(ownerKey, yearMonth, pageRequest)
                    .map(TransactionResponse::from);
        }
        return findPage(ownerKey, pageRequest)
                .map(TransactionResponse::from);
    }

    @GetMapping("/summary")
    public MonthlySummaryResponse summary(@RequestParam String month, HttpServletRequest request) {
        YearMonth yearMonth = parseMonth(month);
        List<Transaction> transactions = findAll(authenticatedUser(request).ownerKey(), yearMonth);
        BigDecimal incomeTotal = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseTotal = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> incomeByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        transactions.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate).thenComparing(Transaction::getCreatedAt))
                .forEach(transaction -> {
                    Map<String, BigDecimal> target = transaction.getType() == TransactionType.INCOME ? incomeByCategory : expenseByCategory;
                    target.merge(transaction.getCategory(), transaction.getAmount(), BigDecimal::add);
                });
        List<MonthlySummaryResponse.CategorySummary> incomeCategorySummaries = incomeByCategory.entrySet().stream()
                .map(entry -> new MonthlySummaryResponse.CategorySummary(entry.getKey(), entry.getValue()))
                .toList();
        List<MonthlySummaryResponse.CategorySummary> expenseCategorySummaries = expenseByCategory.entrySet().stream()
                .map(entry -> new MonthlySummaryResponse.CategorySummary(entry.getKey(), entry.getValue()))
                .toList();
        return new MonthlySummaryResponse(month, incomeTotal, expenseTotal, incomeTotal.subtract(expenseTotal), incomeCategorySummaries, expenseCategorySummaries);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "month must use yyyy-MM format");
        }
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id, HttpServletRequest request) {
        return findById(id, authenticatedUser(request).ownerKey())
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
    }

    @PatchMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @RequestBody UpdateTransactionRequest request, HttpServletRequest httpRequest) {
        return findById(id, authenticatedUser(httpRequest).ownerKey())
                .map(transaction -> {
                    transaction.update(request.transactionDate(), request.amount(), request.merchant(), TransactionType.fromLabel(request.type()), request.category(), request.status());
                    return TransactionResponse.from(transactionRepository.save(transaction));
                })
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String ownerKey = authenticatedUser(request).ownerKey();
        if (!existsById(id, ownerKey)) {
            throw new ResponseStatusException(NOT_FOUND, "Transaction not found");
        }
        transactionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(ApiTokenFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        return new AuthenticatedUser("default");
    }

    private Page<Transaction> findPage(String ownerKey, PageRequest pageRequest) {
        if (UserTokenResolver.DEFAULT_OWNER_KEY.equals(ownerKey)) {
            return transactionRepository.findAllVisibleToDefaultOwner(pageRequest);
        }
        return transactionRepository.findAllByOwnerKeyOrderByTransactionDateDescCreatedAtDesc(ownerKey, pageRequest);
    }

    private Page<Transaction> findPage(String ownerKey, YearMonth yearMonth, PageRequest pageRequest) {
        if (UserTokenResolver.DEFAULT_OWNER_KEY.equals(ownerKey)) {
            return transactionRepository.findAllVisibleToDefaultOwnerBetween(
                    yearMonth.atDay(1),
                    yearMonth.plusMonths(1).atDay(1),
                    pageRequest
            );
        }
        return transactionRepository.findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDescCreatedAtDesc(
                ownerKey,
                yearMonth.atDay(1),
                yearMonth.plusMonths(1).atDay(1),
                pageRequest
        );
    }

    private List<Transaction> findAll(String ownerKey, YearMonth yearMonth) {
        if (UserTokenResolver.DEFAULT_OWNER_KEY.equals(ownerKey)) {
            return transactionRepository.findAllVisibleToDefaultOwnerBetween(yearMonth.atDay(1), yearMonth.plusMonths(1).atDay(1));
        }
        return transactionRepository.findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(ownerKey, yearMonth.atDay(1), yearMonth.plusMonths(1).atDay(1));
    }

    private Optional<Transaction> findById(Long id, String ownerKey) {
        if (UserTokenResolver.DEFAULT_OWNER_KEY.equals(ownerKey)) {
            return transactionRepository.findByIdVisibleToDefaultOwner(id);
        }
        return transactionRepository.findByIdAndOwnerKey(id, ownerKey);
    }

    private boolean existsById(Long id, String ownerKey) {
        if (UserTokenResolver.DEFAULT_OWNER_KEY.equals(ownerKey)) {
            return transactionRepository.existsByIdVisibleToDefaultOwner(id);
        }
        return transactionRepository.existsByIdAndOwnerKey(id, ownerKey);
    }
}

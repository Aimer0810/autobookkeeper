package com.autobookkeeper.api;

import com.autobookkeeper.accounting.AccountingEngine;
import com.autobookkeeper.accounting.BillImportService;
import com.autobookkeeper.api.dto.MonthlySummaryResponse;
import com.autobookkeeper.api.dto.TransactionResponse;
import com.autobookkeeper.api.dto.TrendResponse;
import com.autobookkeeper.api.dto.UpdateTransactionRequest;
import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.domain.TransactionType;
import com.autobookkeeper.repository.TransactionRepository;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
    private final BillImportService billImportService;
    private final AccountingEngine accountingEngine;

    public TransactionController(TransactionRepository transactionRepository, BillImportService billImportService, AccountingEngine accountingEngine) {
        this.transactionRepository = transactionRepository;
        this.billImportService = billImportService;
        this.accountingEngine = accountingEngine;
    }

    @GetMapping
    public Page<TransactionResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String month,
                                          HttpServletRequest request) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        String ownerKey = AuthenticatedUser.fromRequest(request).ownerKey();
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
        List<Transaction> transactions = findAll(AuthenticatedUser.fromRequest(request).ownerKey(), yearMonth);
        BigDecimal incomeTotal = sumByType(transactions, TransactionType.INCOME);
        BigDecimal expenseTotal = sumByType(transactions, TransactionType.EXPENSE);
        Map<String, BigDecimal> incomeByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        transactions.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate).thenComparing(Transaction::getCreatedAt))
                .forEach(transaction -> {
                    Map<String, BigDecimal> target = transaction.getType() == TransactionType.INCOME ? incomeByCategory : expenseByCategory;
                    target.merge(transaction.getCategory(), transaction.getAmount(), BigDecimal::add);
                });
        return new MonthlySummaryResponse(
                month, incomeTotal, expenseTotal, incomeTotal.subtract(expenseTotal),
                toCategorySummaries(incomeByCategory),
                toCategorySummaries(expenseByCategory)
        );
    }

    @GetMapping("/trend")
    public TrendResponse trend(@RequestParam(defaultValue = "6") int months, HttpServletRequest request) {
        String ownerKey = AuthenticatedUser.fromRequest(request).ownerKey();
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(months - 1);
        LocalDate startDate = start.atDay(1);
        LocalDate endDate = end.plusMonths(1).atDay(1);
        List<Transaction> transactions;
        if (UserTokenResolver.DEFAULT_OWNER_KEY.equals(ownerKey)) {
            transactions = transactionRepository.findAllVisibleToDefaultOwnerBetween(startDate, endDate);
        } else {
            transactions = transactionRepository.findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(ownerKey, startDate, endDate);
        }
        Map<String, BigDecimal[]> monthlyData = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            String key = start.plusMonths(i).toString();
            monthlyData.put(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Transaction tx : transactions) {
            String key = YearMonth.from(tx.getTransactionDate()).toString();
            BigDecimal[] totals = monthlyData.get(key);
            if (totals != null) {
                if (tx.getType() == TransactionType.INCOME) {
                    totals[0] = totals[0].add(tx.getAmount());
                } else {
                    totals[1] = totals[1].add(tx.getAmount());
                }
            }
        }
        List<TrendResponse.MonthData> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : monthlyData.entrySet()) {
            result.add(new TrendResponse.MonthData(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return new TrendResponse(result);
    }

    private BigDecimal sumByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<MonthlySummaryResponse.CategorySummary> toCategorySummaries(Map<String, BigDecimal> categoryMap) {
        return categoryMap.entrySet().stream()
                .map(entry -> new MonthlySummaryResponse.CategorySummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "month must use yyyy-MM format");
        }
    }

    @PostMapping("/import")
    public Map<String, Object> importBills(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String ownerKey = AuthenticatedUser.fromRequest(request).ownerKey();
        try {
            List<Bill> bills = billImportService.parse(file.getBytes(), file.getOriginalFilename());
            int imported = 0;
            for (Bill bill : bills) {
                Transaction tx = accountingEngine.createTransaction(bill, "csv-import");
                tx.assignOwner(ownerKey);
                transactionRepository.save(tx);
                imported++;
            }
            return Map.of("status", "ok", "imported", imported, "total", bills.size());
        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            return Map.of("status", "error", "message", "文件解析失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id, HttpServletRequest request) {
        return findById(id, AuthenticatedUser.fromRequest(request).ownerKey())
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
    }

    @PatchMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @RequestBody UpdateTransactionRequest request, HttpServletRequest httpRequest) {
        return findById(id, AuthenticatedUser.fromRequest(httpRequest).ownerKey())
                .map(transaction -> {
                    transaction.update(request.transactionDate(), request.amount(), request.merchant(), TransactionType.fromLabel(request.type()), request.category(), request.status());
                    return TransactionResponse.from(transactionRepository.save(transaction));
                })
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String ownerKey = AuthenticatedUser.fromRequest(request).ownerKey();
        if (!existsById(id, ownerKey)) {
            throw new ResponseStatusException(NOT_FOUND, "Transaction not found");
        }
        transactionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
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

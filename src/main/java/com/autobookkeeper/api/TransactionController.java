package com.autobookkeeper.api;

import com.autobookkeeper.api.dto.TransactionResponse;
import com.autobookkeeper.api.dto.UpdateTransactionRequest;
import com.autobookkeeper.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
}

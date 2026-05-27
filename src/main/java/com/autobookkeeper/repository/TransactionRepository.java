package com.autobookkeeper.repository;

import com.autobookkeeper.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findAllByOrderByTransactionDateDescCreatedAtDesc(Pageable pageable);

    Page<Transaction> findAllByOwnerKeyOrderByTransactionDateDescCreatedAtDesc(String ownerKey, Pageable pageable);

    Page<Transaction> findAllByTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDescCreatedAtDesc(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Transaction> findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDescCreatedAtDesc(String ownerKey, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Transaction> findAllByTransactionDateGreaterThanEqualAndTransactionDateLessThan(LocalDate startDate, LocalDate endDate);

    List<Transaction> findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(String ownerKey, LocalDate startDate, LocalDate endDate);

    Optional<Transaction> findByIdAndOwnerKey(Long id, String ownerKey);

    boolean existsByIdAndOwnerKey(Long id, String ownerKey);
}

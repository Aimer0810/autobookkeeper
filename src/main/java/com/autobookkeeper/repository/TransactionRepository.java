package com.autobookkeeper.repository;

import com.autobookkeeper.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findAllByOrderByTransactionDateDescCreatedAtDesc(Pageable pageable);

    List<Transaction> findAllByTransactionDateGreaterThanEqualAndTransactionDateLessThan(LocalDate startDate, LocalDate endDate);
}

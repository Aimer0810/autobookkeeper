package com.autobookkeeper.repository;

import com.autobookkeeper.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findAllByOrderByTransactionDateDescCreatedAtDesc(Pageable pageable);

    Page<Transaction> findAllByOwnerKeyOrderByTransactionDateDescCreatedAtDesc(String ownerKey, Pageable pageable);

    @Query("""
            select transaction from Transaction transaction
            where transaction.ownerKey = 'default' or transaction.ownerKey is null
            order by transaction.transactionDate desc, transaction.createdAt desc
            """)
    Page<Transaction> findAllVisibleToDefaultOwner(Pageable pageable);

    Page<Transaction> findAllByTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDescCreatedAtDesc(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Transaction> findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThanOrderByTransactionDateDescCreatedAtDesc(String ownerKey, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("""
            select transaction from Transaction transaction
            where (transaction.ownerKey = 'default' or transaction.ownerKey is null)
              and transaction.transactionDate >= :startDate
              and transaction.transactionDate < :endDate
            order by transaction.transactionDate desc, transaction.createdAt desc
            """)
    Page<Transaction> findAllVisibleToDefaultOwnerBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Transaction> findAllByTransactionDateGreaterThanEqualAndTransactionDateLessThan(LocalDate startDate, LocalDate endDate);

    List<Transaction> findAllByOwnerKeyAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(String ownerKey, LocalDate startDate, LocalDate endDate);

    @Query("""
            select transaction from Transaction transaction
            where (transaction.ownerKey = 'default' or transaction.ownerKey is null)
              and transaction.transactionDate >= :startDate
              and transaction.transactionDate < :endDate
            """)
    List<Transaction> findAllVisibleToDefaultOwnerBetween(LocalDate startDate, LocalDate endDate);

    Optional<Transaction> findByIdAndOwnerKey(Long id, String ownerKey);

    @Query("""
            select transaction from Transaction transaction
            where transaction.id = :id
              and (transaction.ownerKey = 'default' or transaction.ownerKey is null)
            """)
    Optional<Transaction> findByIdVisibleToDefaultOwner(Long id);

    boolean existsByIdAndOwnerKey(Long id, String ownerKey);

    @Query("""
            select count(transaction) > 0 from Transaction transaction
            where transaction.id = :id
              and (transaction.ownerKey = 'default' or transaction.ownerKey is null)
            """)
    boolean existsByIdVisibleToDefaultOwner(Long id);

    @Modifying
    @Query("""
            update Transaction transaction
            set transaction.ownerKey = :ownerKey
            where transaction.ownerKey = 'default' or transaction.ownerKey is null
            """)
    int migrateLegacyTransactionsToOwner(@Param("ownerKey") String ownerKey);
}

package com.example.bank.repository;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.bank.entity.BankTransaction;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long>{
    @Query("""
        SELECT t
        FROM BankTransaction t
        WHERE t.fromAccount.id = :accountId
           OR t.toAccount.id = :accountId
        ORDER BY t.creatAd DESC, t.id DESC
        """)
    Page<BankTransaction> findHistoryByAccountId (@Param("accountId") Long accountId, Pageable pageable);
    Optional<BankTransaction>  findByInitiatedByIdAndIdempotencyKey(Long userId, String idempotencyKey);
}

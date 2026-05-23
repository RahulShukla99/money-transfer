package com.example.moneytransfer.infrastructure.persistence;

import com.example.moneytransfer.domain.model.TransactionRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, UUID> {

    Optional<TransactionRecord> findByIdempotencyKey(String idempotencyKey);
}

package com.example.moneytransfer.application.service;

import com.example.moneytransfer.application.dto.TransferCommand;
import com.example.moneytransfer.application.dto.TransferResult;
import com.example.moneytransfer.domain.exception.AccountNotFoundException;
import com.example.moneytransfer.domain.exception.InvalidTransferException;
import com.example.moneytransfer.domain.model.Account;
import com.example.moneytransfer.domain.model.Money;
import com.example.moneytransfer.domain.model.TransactionRecord;
import com.example.moneytransfer.domain.model.TransactionStatus;
import com.example.moneytransfer.infrastructure.persistence.AccountRepository;
import com.example.moneytransfer.infrastructure.persistence.TransactionRecordRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String DEFAULT_CURRENCY = "USD";

    private final AccountRepository accountRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final LocalIdempotencyLockRegistry localIdempotencyLockRegistry;
    private final TransactionTemplate transferTransaction;
    private final TransactionTemplate requiresNewTransaction;

    public TransferService(
            AccountRepository accountRepository,
            TransactionRecordRepository transactionRecordRepository,
            LocalIdempotencyLockRegistry localIdempotencyLockRegistry,
            PlatformTransactionManager transactionManager
    ) {
        this.accountRepository = accountRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.localIdempotencyLockRegistry = localIdempotencyLockRegistry;
        this.transferTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TransferResult transfer(TransferCommand command) {
        validateCommand(command);
        String idempotencyKey = command.idempotencyKey().trim();
        log.info(
                "Transfer request accepted for local serialization: fromAccountId={}, toAccountId={}, amount={}, idempotencyKey={}",
                command.fromAccountId(),
                command.toAccountId(),
                command.amount(),
                idempotencyKey
        );
        return localIdempotencyLockRegistry.executeWithLock(idempotencyKey, () -> doTransfer(command, idempotencyKey));
    }

    private TransferResult doTransfer(TransferCommand command, String idempotencyKey) {
        Money money = new Money(command.amount(), DEFAULT_CURRENCY);
        log.info("Transfer processing started: idempotencyKey={}", idempotencyKey);

        IdempotencyReservation reservation = startOrGetExisting(command, money, idempotencyKey);
        if (!reservation.created()) {
            ensureIdempotentReplayMatchesOriginalRequest(reservation.record(), command, money);
            log.info(
                    "Idempotent transfer replay detected: transactionId={}, status={}, idempotencyKey={}",
                    reservation.record().getId(),
                    reservation.record().getStatus(),
                    idempotencyKey
            );
            return TransferResult.from(reservation.record());
        }

        try {
            return executeTransfer(command, money, reservation.record().getId());
        } catch (RuntimeException ex) {
            log.warn(
                    "Transfer failed; marking transaction as FAILED: transactionId={}, idempotencyKey={}, reason={}",
                    reservation.record().getId(),
                    idempotencyKey,
                    ex.getMessage()
            );
            markFailed(reservation.record().getId(), ex.getMessage());
            throw ex;
        }
    }

    private IdempotencyReservation startOrGetExisting(TransferCommand command, Money money, String idempotencyKey) {
        return transactionRecordRepository.findByIdempotencyKey(idempotencyKey)
                .map(record -> new IdempotencyReservation(record, false))
                .orElseGet(() -> createReservation(command, money, idempotencyKey));
    }

    private IdempotencyReservation createReservation(TransferCommand command, Money money, String idempotencyKey) {
        try {
            TransactionRecord record = requiresNewTransaction.execute(status -> transactionRecordRepository.saveAndFlush(
                    new TransactionRecord(command.fromAccountId(), command.toAccountId(), money, idempotencyKey)
            ));
            log.info(
                    "Transfer transaction record reserved: transactionId={}, fromAccountId={}, toAccountId={}, amount={}, currency={}, idempotencyKey={}",
                    record.getId(),
                    record.getFromAccountId(),
                    record.getToAccountId(),
                    record.getAmount(),
                    record.getCurrency(),
                    record.getIdempotencyKey()
            );
            return new IdempotencyReservation(record, true);
        } catch (DataIntegrityViolationException ex) {
            TransactionRecord existing = transactionRecordRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            log.info(
                    "Concurrent idempotency reservation found existing transaction: transactionId={}, status={}, idempotencyKey={}",
                    existing.getId(),
                    existing.getStatus(),
                    idempotencyKey
            );
            return new IdempotencyReservation(existing, false);
        }
    }

    private TransferResult executeTransfer(TransferCommand command, Money money, UUID transactionId) {
        return transferTransaction.execute(status -> {
            log.info("Transfer database transaction started: transactionId={}", transactionId);
            TransactionRecord record = transactionRecordRepository.findById(transactionId)
                    .orElseThrow(() -> new IllegalStateException("Transaction record disappeared: " + transactionId));

            if (record.getStatus() != TransactionStatus.PROCESSING) {
                log.info(
                        "Transfer database transaction skipped because record is already terminal: transactionId={}, status={}",
                        record.getId(),
                        record.getStatus()
                );
                return TransferResult.from(record);
            }

            List<UUID> lockOrder = List.of(command.fromAccountId(), command.toAccountId())
                    .stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            log.debug("Locking account rows for transfer: transactionId={}, lockOrder={}", transactionId, lockOrder);

            Account first = accountRepository.findByIdForUpdate(lockOrder.get(0))
                    .orElseThrow(() -> new AccountNotFoundException(lockOrder.get(0)));
            Account second = accountRepository.findByIdForUpdate(lockOrder.get(1))
                    .orElseThrow(() -> new AccountNotFoundException(lockOrder.get(1)));
            log.debug("Account rows locked for transfer: transactionId={}, firstAccountId={}, secondAccountId={}",
                    transactionId,
                    first.getId(),
                    second.getId()
            );

            Account from = first.getId().equals(command.fromAccountId()) ? first : second;
            Account to = first.getId().equals(command.toAccountId()) ? first : second;

            log.info(
                    "Applying debit and credit: transactionId={}, fromAccountId={}, toAccountId={}, amount={}, currency={}",
                    transactionId,
                    from.getId(),
                    to.getId(),
                    money.amount(),
                    money.currency()
            );
            from.debit(money);
            to.credit(money);
            record.markSuccess();
            log.info("Transfer completed successfully: transactionId={}, idempotencyKey={}",
                    record.getId(),
                    record.getIdempotencyKey()
            );

            return TransferResult.from(record);
        });
    }

    private void markFailed(UUID transactionId, String reason) {
        requiresNewTransaction.executeWithoutResult(status -> transactionRecordRepository.findById(transactionId)
                .ifPresent(record -> {
                    record.markFailed(reason == null ? "Transfer failed" : reason);
                    log.info("Transfer transaction record marked FAILED: transactionId={}, reason={}",
                            transactionId,
                            record.getMessage()
                    );
                }));
    }

    private void validateCommand(TransferCommand command) {
        if (command.fromAccountId() == null || command.toAccountId() == null) {
            throw new InvalidTransferException("Both account IDs are required");
        }
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new InvalidTransferException("Source and destination accounts must be different");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new InvalidTransferException("Idempotency key is required");
        }
    }

    private void ensureIdempotentReplayMatchesOriginalRequest(
            TransactionRecord record,
            TransferCommand command,
            Money money
    ) {
        boolean sameRequest = record.getFromAccountId().equals(command.fromAccountId())
                && record.getToAccountId().equals(command.toAccountId())
                && record.getAmount().compareTo(money.amount()) == 0
                && record.getCurrency().equals(money.currency());

        if (!sameRequest) {
            log.warn(
                    "Idempotency key conflict: transactionId={}, idempotencyKey={}, originalFromAccountId={}, originalToAccountId={}, requestFromAccountId={}, requestToAccountId={}",
                    record.getId(),
                    record.getIdempotencyKey(),
                    record.getFromAccountId(),
                    record.getToAccountId(),
                    command.fromAccountId(),
                    command.toAccountId()
            );
            throw new InvalidTransferException("Idempotency key was already used for a different transfer request");
        }
    }

    private record IdempotencyReservation(TransactionRecord record, boolean created) {
    }
}

package com.example.moneytransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.moneytransfer.application.dto.TransferCommand;
import com.example.moneytransfer.domain.exception.InvalidTransferException;
import com.example.moneytransfer.domain.exception.InsufficientFundsException;
import com.example.moneytransfer.domain.model.Account;
import com.example.moneytransfer.domain.model.TransactionStatus;
import com.example.moneytransfer.infrastructure.persistence.AccountRepository;
import com.example.moneytransfer.infrastructure.persistence.TransactionRecordRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransferServiceTest {

    private static final UUID FROM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private LocalIdempotencyLockRegistry localIdempotencyLockRegistry;

    @BeforeEach
    void setUp() {
        transactionRecordRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.save(new Account(FROM, new BigDecimal("100.00"), "USD"));
        accountRepository.save(new Account(TO, new BigDecimal("10.00"), "USD"));
    }

    @Test
    void transfersMoneyAtomically() {
        var result = transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("25.00"), "key-1"));

        assertThat(result.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(accountRepository.findById(FROM).orElseThrow().getBalance()).isEqualByComparingTo("75.00");
        assertThat(accountRepository.findById(TO).orElseThrow().getBalance()).isEqualByComparingTo("35.00");
    }

    @Test
    void returnsExistingResultForSameIdempotencyKey() {
        var first = transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("25.00"), "same-key"));
        var second = transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("25.00"), "same-key"));

        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(accountRepository.findById(FROM).orElseThrow().getBalance()).isEqualByComparingTo("75.00");
        assertThat(accountRepository.findById(TO).orElseThrow().getBalance()).isEqualByComparingTo("35.00");
    }

    @Test
    void rejectsSameIdempotencyKeyForDifferentRequest() {
        transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("25.00"), "reused-key"));

        assertThatThrownBy(() -> transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("30.00"), "reused-key")))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void rejectsInsufficientFundsAndStoresFailedRecord() {
        assertThatThrownBy(() -> transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("500.00"), "key-2")))
                .isInstanceOf(InsufficientFundsException.class);

        var record = transactionRecordRepository.findByIdempotencyKey("key-2").orElseThrow();
        assertThat(record.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(accountRepository.findById(FROM).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void handlesConcurrentRequestsWithSameIdempotencyKey() throws Exception {
        int workers = 8;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(workers);
        var executor = Executors.newFixedThreadPool(workers);

        for (int i = 0; i < workers; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    transferService.transfer(new TransferCommand(FROM, TO, new BigDecimal("10.00"), "concurrent-key"));
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(transactionRecordRepository.findAll()).hasSize(1);
        assertThat(accountRepository.findById(FROM).orElseThrow().getBalance()).isEqualByComparingTo("90.00");
        assertThat(accountRepository.findById(TO).orElseThrow().getBalance()).isEqualByComparingTo("20.00");
        assertThat(localIdempotencyLockRegistry.activeLockCount()).isZero();
    }
}

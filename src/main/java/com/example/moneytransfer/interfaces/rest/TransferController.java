package com.example.moneytransfer.interfaces.rest;

import com.example.moneytransfer.application.dto.TransferCommand;
import com.example.moneytransfer.application.dto.TransferResult;
import com.example.moneytransfer.application.service.TransferService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResult> transfer(@Valid @RequestBody TransferRequest request) {
        log.info(
                "HTTP transfer request received: fromAccountId={}, toAccountId={}, amount={}, idempotencyKey={}",
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.idempotencyKey()
        );
        TransferResult result = transferService.transfer(new TransferCommand(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.idempotencyKey()
        ));
        log.info(
                "HTTP transfer request completed: transactionId={}, status={}, idempotencyKey={}",
                result.transactionId(),
                result.status(),
                result.idempotencyKey()
        );
        return ResponseEntity.ok(result);
    }
}

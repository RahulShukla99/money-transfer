package com.example.moneytransfer.interfaces.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.moneytransfer.domain.model.Account;
import com.example.moneytransfer.infrastructure.persistence.AccountRepository;
import com.example.moneytransfer.infrastructure.persistence.TransactionRecordRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferControllerTest {

    private static final UUID FROM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @BeforeEach
    void setUp() {
        transactionRecordRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.save(new Account(FROM, new BigDecimal("100.00"), "USD"));
        accountRepository.save(new Account(TO, new BigDecimal("10.00"), "USD"));
    }

    @Test
    void receivesTransferRequest() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                                  "amount": 25.00,
                                  "idempotencyKey": "http-request-key"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.message", is("Transfer completed")))
                .andExpect(jsonPath("$.fromAccountId", is(FROM.toString())))
                .andExpect(jsonPath("$.toAccountId", is(TO.toString())))
                .andExpect(jsonPath("$.amount", is(25.0)))
                .andExpect(jsonPath("$.idempotencyKey", is("http-request-key")));
    }
}

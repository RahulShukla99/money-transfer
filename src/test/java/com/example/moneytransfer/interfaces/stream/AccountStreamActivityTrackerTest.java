package com.example.moneytransfer.interfaces.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountStreamActivityTrackerTest {

    private static final UUID FROM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void findsAccountActivitySinceTimestampUsingTailMap() {
        AccountStreamActivityTracker tracker = new AccountStreamActivityTracker();
        TransferStreamMessage message = new TransferStreamMessage(
                FROM,
                TO,
                new BigDecimal("25.00"),
                "stream-request-key"
        );

        tracker.recordReceived(message);
        Instant afterReceived = Instant.now();
        tracker.recordProcessing(message);
        tracker.recordSuccess(message);

        List<AccountStreamActivity> recentActivity = tracker.findActivitySince(FROM, afterReceived);

        assertThat(recentActivity)
                .extracting(AccountStreamActivity::stage)
                .containsExactly(AccountStreamActivityStage.PROCESSING, AccountStreamActivityStage.SUCCESS);
    }
}

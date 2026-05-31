package com.example.moneytransfer.interfaces.stream;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "money-transfer.stream.kafka", name = "enabled", havingValue = "true")
public class AccountStreamActivityTracker {

    private final ConcurrentHashMap<UUID, ConcurrentSkipListMap<Instant, AccountStreamActivity>> accountActivity =
            new ConcurrentHashMap<>();

    public void recordReceived(TransferStreamMessage message) {
        record(message, AccountStreamActivityStage.RECEIVED, null);
    }

    public void recordProcessing(TransferStreamMessage message) {
        record(message, AccountStreamActivityStage.PROCESSING, null);
    }

    public void recordSuccess(TransferStreamMessage message) {
        record(message, AccountStreamActivityStage.SUCCESS, "Transfer completed");
    }

    public void recordFailed(TransferStreamMessage message, String reason) {
        record(message, AccountStreamActivityStage.FAILED, reason);
    }

    public List<AccountStreamActivity> findRecentActivity(UUID accountId, Duration lookback) {
        Instant from = Instant.now().minus(lookback);
        return findActivitySince(accountId, from);
    }

    List<AccountStreamActivity> findActivitySince(UUID accountId, Instant from) {
        ConcurrentSkipListMap<Instant, AccountStreamActivity> timeline = accountActivity.get(accountId);
        if (timeline == null) {
            return List.of();
        }
        return List.copyOf(timeline.tailMap(from).values());
    }

    private void record(TransferStreamMessage message, AccountStreamActivityStage stage, String activityMessage) {
        Instant occurredAt = Instant.now();
        recordOneSide(
                occurredAt,
                message.fromAccountId(),
                message.toAccountId(),
                message,
                AccountStreamActivityDirection.OUTGOING,
                stage,
                activityMessage
        );
        recordOneSide(
                occurredAt,
                message.toAccountId(),
                message.fromAccountId(),
                message,
                AccountStreamActivityDirection.INCOMING,
                stage,
                activityMessage
        );
    }

    private void recordOneSide(
            Instant occurredAt,
            UUID accountId,
            UUID otherAccountId,
            TransferStreamMessage message,
            AccountStreamActivityDirection direction,
            AccountStreamActivityStage stage,
            String activityMessage
    ) {
        AccountStreamActivity activity = new AccountStreamActivity(
                uniqueTimestamp(occurredAt, accountId),
                accountId,
                otherAccountId,
                message.amount(),
                message.idempotencyKey(),
                direction,
                stage,
                activityMessage
        );
        accountActivity
                .computeIfAbsent(accountId, ignored -> new ConcurrentSkipListMap<>())
                .put(activity.occurredAt(), activity);
    }

    private Instant uniqueTimestamp(Instant occurredAt, UUID accountId) {
        ConcurrentSkipListMap<Instant, AccountStreamActivity> timeline = accountActivity
                .computeIfAbsent(accountId, ignored -> new ConcurrentSkipListMap<>());
        Instant candidate = occurredAt;
        while (timeline.containsKey(candidate)) {
            candidate = candidate.plusNanos(1);
        }
        return candidate;
    }
}

package com.example.moneytransfer.interfaces.rest;

import com.example.moneytransfer.interfaces.stream.AccountStreamActivity;
import com.example.moneytransfer.interfaces.stream.AccountStreamActivityTracker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/accounts")
@ConditionalOnBean(AccountStreamActivityTracker.class)
public class AccountStreamActivityController {

    private final AccountStreamActivityTracker activityTracker;

    public AccountStreamActivityController(AccountStreamActivityTracker activityTracker) {
        this.activityTracker = activityTracker;
    }

    @GetMapping("/{accountId}/stream-activity")
    public ResponseEntity<List<AccountStreamActivity>> recentActivity(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "60") long minutes
    ) {
        return ResponseEntity.ok(activityTracker.findRecentActivity(accountId, Duration.ofMinutes(minutes)));
    }
}

package com.example.moneytransfer.domain.model;

import com.example.moneytransfer.domain.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Version
    private long version;

    protected Account() {
    }

    public Account(UUID id, BigDecimal balance, String currency) {
        this.id = id;
        this.balance = balance;
        this.currency = currency;
    }

    public void debit(Money amount) {
        ensureSameCurrency(amount);
        if (balance.compareTo(amount.amount()) < 0) {
            throw new InsufficientFundsException(id);
        }
        balance = balance.subtract(amount.amount());
    }

    public void credit(Money amount) {
        ensureSameCurrency(amount);
        balance = balance.add(amount.amount());
    }

    private void ensureSameCurrency(Money amount) {
        if (!currency.equals(amount.currency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }
}

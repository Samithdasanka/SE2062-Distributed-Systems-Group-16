package com.group16.distributed.payments.ledger;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LedgerStateMachine {
    private final List<LedgerEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet(); // dedup

    public boolean hasProcessed(String idempotencyKey) {
        return idempotencyKey != null && processedKeys.contains(idempotencyKey);
    }

    public synchronized void apply(LedgerEntry e) {
        if (e.idempotencyKey != null && processedKeys.contains(e.idempotencyKey)) {
            return; // dedup
        }
        if (e.idempotencyKey != null) processedKeys.add(e.idempotencyKey);

        balances.putIfAbsent(e.from, BigDecimal.ZERO);
        balances.putIfAbsent(e.to, BigDecimal.ZERO);

        balances.put(e.from, balances.get(e.from).subtract(e.amount));
        balances.put(e.to, balances.get(e.to).add(e.amount));

        e.success = true;
        entries.add(e);
    }

    public List<LedgerEntry> getEntries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public BigDecimal getBalance(String account) {
        return balances.getOrDefault(account, BigDecimal.ZERO);
    }
}
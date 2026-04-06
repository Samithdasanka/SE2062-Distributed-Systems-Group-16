package com.group16.distributed.payments.ledger;

import java.math.BigDecimal;

public class LedgerEntry {
    public long logIndex;
    public String from;
    public String to;
    public BigDecimal amount;

    public String idempotencyKey;

    public long clientTimestampMs;
    public long correctedTimestampMs;

    public boolean success;
}
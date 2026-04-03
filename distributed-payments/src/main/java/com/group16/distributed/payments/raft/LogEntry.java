package com.group16.distributed.payments.raft;

public class LogEntry {
    public long index;
    public long term;

    public String commandType;   // "PAYMENT"
    public String commandJson;   // serialized request
    public String idempotencyKey;

    public long clientTimestampMs;
}
package com.group16.distributed.payments.raft;

public interface StateMachine {
    void apply(long logIndex, String commandType, String commandJson, long clientTimestampMs);
    boolean isDuplicate(String idempotencyKey);
}
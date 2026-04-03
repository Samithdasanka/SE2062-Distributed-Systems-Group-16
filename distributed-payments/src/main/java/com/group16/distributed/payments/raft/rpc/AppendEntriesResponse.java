package com.group16.distributed.payments.raft.rpc;

public class AppendEntriesResponse {
    public long term;
    public boolean success;
    public long conflictIndex;
}
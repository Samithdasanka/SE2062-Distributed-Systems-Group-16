package com.group16.distributed.payments.raft.rpc;

public class RequestVoteRequest {
    public String candidateId;
    public long term;
    public long lastLogIndex;
    public long lastLogTerm;
}
package com.group16.distributed.payments.raft.rpc;

public class RequestVoteResponse {
    public long term;
    public boolean voteGranted;
}
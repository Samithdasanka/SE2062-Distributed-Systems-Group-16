package com.group16.distributed.payments.raft.rpc;

import com.group16.distributed.payments.raft.LogEntry;
import java.util.List;

public class AppendEntriesRequest {
    public String leaderId;
    public String leaderUrl;
    public long term;

    public long prevLogIndex;
    public long prevLogTerm;

    public List<LogEntry> entries; // empty = heartbeat
    public long leaderCommit;
}
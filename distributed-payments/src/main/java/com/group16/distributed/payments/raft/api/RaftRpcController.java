package com.group16.distributed.payments.raft.api;

import com.group16.distributed.payments.raft.RaftNode;
import com.group16.distributed.payments.raft.rpc.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/raft")
public class RaftRpcController {

    private final RaftNode raftNode;

    public RaftRpcController(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @PostMapping("/requestVote")
    public RequestVoteResponse requestVote(@RequestBody RequestVoteRequest req) {
        return raftNode.onRequestVote(req);
    }

    @PostMapping("/appendEntries")
    public AppendEntriesResponse appendEntries(@RequestBody AppendEntriesRequest req) {
        return raftNode.onAppendEntries(req);
    }
}
package com.group16.distributed.payments.api;

import com.group16.distributed.payments.raft.RaftNode;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final RaftNode raftNode;

    public HealthController(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", raftNode.selfId());
        m.put("role", raftNode.role().name());
        m.put("term", raftNode.term());
        m.put("leaderId", raftNode.leaderId());
        m.put("leaderUrl", raftNode.leaderUrl());
        m.put("commitIndex", raftNode.commitIndex());
        return m;
    }
}
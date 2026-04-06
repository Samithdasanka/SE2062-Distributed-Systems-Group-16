package com.group16.distributed.payments.raft.peer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PeerHealthTracker {
    private final Map<String, Boolean> peerStatus = new ConcurrentHashMap<>();

    public void markOk(String peerId) {
        peerStatus.put(peerId, true);
    }

    public void markFail(String peerId) {
        peerStatus.put(peerId, false);
    }
}

package com.group16.distributed.payments.time;

import com.group16.distributed.payments.config.NodeProperties;
import com.group16.distributed.payments.config.Peer;
import com.group16.distributed.payments.config.PeerSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cristian-style offset estimation:
 * offset ≈ peerTime - (t0 + t1)/2
 * Uses median of offsets from peers.
 */
@Component
public class TimeOffsetEstimator {
    private final NodeProperties nodeProperties;
    private final PeerSet peerSet;
    private final RestTemplate restTemplate;

    private volatile long offsetMs = 0;

    public TimeOffsetEstimator(NodeProperties nodeProperties, PeerSet peerSet, RestTemplate restTemplate) {
        this.nodeProperties = nodeProperties;
        this.peerSet = peerSet;
        this.restTemplate = restTemplate;
    }

    public long correctedNowMs() {
        return System.currentTimeMillis() + offsetMs;
    }

    public long currentOffsetMs() {
        return offsetMs;
    }

    @Scheduled(fixedDelay = 3000)
    public void refreshOffset() {
        List<Long> offsets = new ArrayList<>();
        for (Peer p : peerSet.others(nodeProperties.getId())) {
            try {
                long t0 = System.currentTimeMillis();
                Long peerTime = restTemplate.getForObject(p.url + "/raft/time", Long.class);
                long t1 = System.currentTimeMillis();
                if (peerTime != null) {
                    long midpoint = (t0 + t1) / 2;
                    offsets.add(peerTime - midpoint);
                }
            } catch (Exception ignored) {
                // peer down or network issue, skip
            }
        }
        if (!offsets.isEmpty()) {
            Collections.sort(offsets);
            this.offsetMs = offsets.get(offsets.size() / 2); // median
        }
    }
}
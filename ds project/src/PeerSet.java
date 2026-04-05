package com.group16.distributed.payments.config;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Components
public class PeerSet {
    private final Map<String, Peer> peersById;

    public PeerSet(ClusterProperties clusterProperties) {
        Map<String, Peer> map = new LinkedHashMap<>();
        if (clusterProperties.getPeers() != null) {
            for (String s : clusterProperties.getPeers()) {
                // "node1@http://localhost:8081"
                String[] parts = s.split("@", 2);
                if (parts.length == 2) {
                    map.put(parts[0], new Peer(parts[0], parts[1]));
                }
            }
        }
        this.peersById = Collections.unmodifiableMap(map);
    }

    public Collection<Peer> all() { return peersById.values(); }

    public List<Peer> others(String selfId) {
        return peersById.values().stream()
                .filter(p -> !p.id.equals(selfId))
                .collect(Collectors.toList());
    }

    public Peer get(String id) { return peersById.get(id); }

    public int size() { return peersById.size(); }

    public int quorumSize() { return (size() / 2) + 1; }
}
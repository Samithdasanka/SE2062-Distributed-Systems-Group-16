package com.group16.distributed.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cluster")
public class ClusterProperties {
    /**
     * Example: node1@http://localhost:8081,node2@http://localhost:8082,node3@http://localhost:8083
     */
    private List<String> peers;

    public List<String> getPeers() { return peers; }
    public void setPeers(List<String> peers) { this.peers = peers; }
}
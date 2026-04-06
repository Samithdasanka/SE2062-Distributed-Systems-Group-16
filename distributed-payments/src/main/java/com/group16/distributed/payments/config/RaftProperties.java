package com.group16.distributed.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raft")
public class RaftProperties {
    private long electionTimeoutMinMs = 1500;
    private long electionTimeoutMaxMs = 3000;
    private long heartbeatIntervalMs = 250;

    public long getElectionTimeoutMinMs() { return electionTimeoutMinMs; }
    public void setElectionTimeoutMinMs(long electionTimeoutMinMs) { this.electionTimeoutMinMs = electionTimeoutMinMs; }

    public long getElectionTimeoutMaxMs() { return electionTimeoutMaxMs; }
    public void setElectionTimeoutMaxMs(long electionTimeoutMaxMs) { this.electionTimeoutMaxMs = electionTimeoutMaxMs; }

    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
}
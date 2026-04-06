package com.group16.distributed.payments.raft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group16.distributed.payments.config.*;
import com.group16.distributed.payments.ledger.LedgerEntry;
import com.group16.distributed.payments.ledger.LedgerService;
import com.group16.distributed.payments.payment.PaymentProcessor;
import com.group16.distributed.payments.payment.PaymentRequest;
import com.group16.distributed.payments.raft.peer.PeerHealthTracker;
import com.group16.distributed.payments.raft.rpc.*;
import com.group16.distributed.payments.time.TimeOffsetEstimator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RaftNode {

    private final NodeProperties nodeProperties;
    private final PeerSet peerSet;
    private final RaftProperties raftProperties;
    private final RestTemplate restTemplate;
    private final PeerHealthTracker peerHealth;
    private final LedgerService ledgerService;
    private final PaymentProcessor paymentProcessor;
    private final TimeOffsetEstimator timeOffsetEstimator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Persistent (in-memory for prototype)
    private volatile long currentTerm = 0;
    private volatile String votedFor = null;
    private final List<LogEntry> log = new ArrayList<>();

    // Volatile
    private volatile RaftRole role = RaftRole.FOLLOWER;
    private volatile String leaderId = null;
    private volatile String leaderUrl = null;

    private volatile long commitIndex = 0;
    private volatile long lastApplied = 0;

    // Leader-only replication state
    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // Election timer
    private volatile long lastHeartbeatOrVoteMs = System.currentTimeMillis();
    private volatile long electionTimeoutMs = randomElectionTimeout();

    public RaftNode(NodeProperties nodeProperties,
                    PeerSet peerSet,
                    RaftProperties raftProperties,
                    RestTemplate restTemplate,
                    PeerHealthTracker peerHealth,
                    LedgerService ledgerService,
                    PaymentProcessor paymentProcessor,
                    TimeOffsetEstimator timeOffsetEstimator) {
        this.nodeProperties = nodeProperties;
        this.peerSet = peerSet;
        this.raftProperties = raftProperties;
        this.restTemplate = restTemplate;
        this.peerHealth = peerHealth;
        this.ledgerService = ledgerService;
        this.paymentProcessor = paymentProcessor;
        this.timeOffsetEstimator = timeOffsetEstimator;

        // index starts at 1 for simplicity
        log.add(dummyEntry0());
    }

    private LogEntry dummyEntry0() {
        LogEntry e = new LogEntry();
        e.index = 0;
        e.term = 0;
        e.commandType = "DUMMY";
        e.commandJson = "{}";
        return e;
    }

    public synchronized RaftRole role() { return role; }
    public synchronized long term() { return currentTerm; }
    public synchronized String leaderId() { return leaderId; }
    public synchronized String leaderUrl() { return leaderUrl; }
    public synchronized long commitIndex() { return commitIndex; }
    public String selfId() { return nodeProperties.getId(); }
    public String selfUrl() { return nodeProperties.getUrl(); }

    private long randomElectionTimeout() {
        long min = raftProperties.getElectionTimeoutMinMs();
        long max = raftProperties.getElectionTimeoutMaxMs();
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    // =========================
    // Member 4: Election logic
    // =========================

    @Scheduled(fixedDelay = 100)
    public void electionTick() {
        if (role == RaftRole.LEADER) return;

        long now = System.currentTimeMillis();
        long elapsed = now - lastHeartbeatOrVoteMs;
        if (elapsed < electionTimeoutMs) return;

        startElection();
    }

    private synchronized void startElection() {
        role = RaftRole.CANDIDATE;
        currentTerm++;
        votedFor = selfId();
        leaderId = null;
        leaderUrl = null;

        lastHeartbeatOrVoteMs = System.currentTimeMillis();
        electionTimeoutMs = randomElectionTimeout();

        long votes = 1; // self vote
        long needed = peerSet.quorumSize();

        long lastIndex = lastLogIndex();
        long lastTerm = logTermAt(lastIndex);

        for (Peer p : peerSet.others(selfId())) {
            try {
                RequestVoteRequest req = new RequestVoteRequest();
                req.candidateId = selfId();
                req.term = currentTerm;
                req.lastLogIndex = lastIndex;
                req.lastLogTerm = lastTerm;

                RequestVoteResponse resp = restTemplate.postForObject(
                        p.url + "/raft/requestVote", req, RequestVoteResponse.class);

                if (resp == null) continue;

                if (resp.term > currentTerm) {
                    becomeFollower(resp.term, null, null);
                    return;
                }
                if (resp.voteGranted) votes++;
                peerHealth.markOk(p.id);
            } catch (Exception ex) {
                peerHealth.markFail(p.id);
            }
        }

        if (votes >= needed) {
            becomeLeader();
        } else {
            // stay candidate/follower until next timeout; become follower to reduce split votes
            role = RaftRole.FOLLOWER;
            votedFor = null;
        }
    }

    private synchronized void becomeFollower(long newTerm, String newLeaderId, String newLeaderUrl) {
        role = RaftRole.FOLLOWER;
        if (newTerm > currentTerm) {
            currentTerm = newTerm;
            votedFor = null;
        }
        leaderId = newLeaderId;
        leaderUrl = newLeaderUrl;

        lastHeartbeatOrVoteMs = System.currentTimeMillis();
        electionTimeoutMs = randomElectionTimeout();
    }

    private synchronized void becomeLeader() {
        role = RaftRole.LEADER;
        leaderId = selfId();
        leaderUrl = selfUrl();

        // init replication indices
        long next = lastLogIndex() + 1;
        for (Peer p : peerSet.others(selfId())) {
            nextIndex.put(p.id, next);
            matchIndex.put(p.id, 0L);
        }
    }

    public synchronized RequestVoteResponse onRequestVote(RequestVoteRequest req) {
        RequestVoteResponse resp = new RequestVoteResponse();

        if (req.term < currentTerm) {
            resp.term = currentTerm;
            resp.voteGranted = false;
            return resp;
        }

        if (req.term > currentTerm) {
            becomeFollower(req.term, null, null);
        }

        boolean upToDate = isCandidateLogUpToDate(req.lastLogIndex, req.lastLogTerm);
        boolean canVote = (votedFor == null || votedFor.equals(req.candidateId));

        if (canVote && upToDate) {
            votedFor = req.candidateId;
            lastHeartbeatOrVoteMs = System.currentTimeMillis();
            resp.voteGranted = true;
        } else {
            resp.voteGranted = false;
        }

        resp.term = currentTerm;
        return resp;
    }

    private boolean isCandidateLogUpToDate(long candidateLastIndex, long candidateLastTerm) {
        long myLastIndex = lastLogIndex();
        long myLastTerm = logTermAt(myLastIndex);
        if (candidateLastTerm != myLastTerm) return candidateLastTerm > myLastTerm;
        return candidateLastIndex >= myLastIndex;
    }

    // =========================
    // Member 2: Replication
    // =========================

    public synchronized AppendEntriesResponse onAppendEntries(AppendEntriesRequest req) {
        AppendEntriesResponse resp = new AppendEntriesResponse();

        if (req.term < currentTerm) {
            resp.term = currentTerm;
            resp.success = false;
            resp.conflictIndex = lastLogIndex();
            return resp;
        }

        // accept leader, step down
        if (req.term > currentTerm || role != RaftRole.FOLLOWER) {
            becomeFollower(req.term, req.leaderId, req.leaderUrl);
        } else {
            leaderId = req.leaderId;
            leaderUrl = req.leaderUrl;
        }

        lastHeartbeatOrVoteMs = System.currentTimeMillis();

        // log consistency check
        if (req.prevLogIndex > lastLogIndex()) {
            resp.term = currentTerm;
            resp.success = false;
            resp.conflictIndex = lastLogIndex();
            return resp;
        }
        if (req.prevLogIndex >= 0 && logTermAt(req.prevLogIndex) != req.prevLogTerm) {
            // conflict: tell leader to back off
            resp.term = currentTerm;
            resp.success = false;
            resp.conflictIndex = Math.max(1, req.prevLogIndex - 1);
            return resp;
        }

        // append entries (overwrite conflicts)
        if (req.entries != null && !req.entries.isEmpty()) {
            for (LogEntry e : req.entries) {
                if (e.index <= lastLogIndex()) {
                    // overwrite if term mismatch
                    if (logTermAt(e.index) != e.term) {
                        truncateFrom(e.index);
                        log.add(e);
                    }
                } else {
                    log.add(e);
                }
            }
        }

        // commit index update
        if (req.leaderCommit > commitIndex) {
            commitIndex = Math.min(req.leaderCommit, lastLogIndex());
        }

        applyCommitted();

        resp.term = currentTerm;
        resp.success = true;
        resp.conflictIndex = 0;
        return resp;
    }

    private synchronized void truncateFrom(long index) {
        while (log.size() > index) {
            log.remove(log.size() - 1);
        }
    }

    private synchronized long lastLogIndex() {
        return log.get(log.size() - 1).index;
    }

    private synchronized long logTermAt(long index) {
        if (index < 0 || index >= log.size()) return 0;
        return log.get((int) index).term;
    }

    @Scheduled(fixedDelayString = "${raft.heartbeatIntervalMs:250}")
    public void leaderHeartbeatAndReplicate() {
        if (role != RaftRole.LEADER) return;

        for (Peer p : peerSet.others(selfId())) {
            replicateToFollower(p);
        }

        advanceCommitIndex();
        applyCommitted();
    }

    private void replicateToFollower(Peer follower) {
        try {
            long ni = nextIndex.getOrDefault(follower.id, lastLogIndex() + 1);
            long prevIndex = ni - 1;
            long prevTerm = logTermAt(prevIndex);

            List<LogEntry> entriesToSend;
            synchronized (this) {
                entriesToSend = new ArrayList<>();
                for (long i = ni; i <= lastLogIndex(); i++) {
                    entriesToSend.add(log.get((int) i));
                }
            }

            AppendEntriesRequest req = new AppendEntriesRequest();
            req.leaderId = selfId();
            req.leaderUrl = selfUrl();
            req.term = term();
            req.prevLogIndex = prevIndex;
            req.prevLogTerm = prevTerm;
            req.entries = entriesToSend;
            req.leaderCommit = commitIndex();

            AppendEntriesResponse resp = restTemplate.postForObject(
                    follower.url + "/raft/appendEntries", req, AppendEntriesResponse.class);

            if (resp == null) return;

            if (resp.term > term()) {
                synchronized (this) {
                    becomeFollower(resp.term, null, null);
                }
                return;
            }

            if (resp.success) {
                peerHealth.markOk(follower.id);

                long match = prevIndex + (entriesToSend == null ? 0 : entriesToSend.size());
                matchIndex.put(follower.id, match);
                nextIndex.put(follower.id, match + 1);
            } else {
                peerHealth.markFail(follower.id);
                long backoff = Math.max(1, resp.conflictIndex);
                nextIndex.put(follower.id, backoff);
            }
        } catch (Exception ex) {
            peerHealth.markFail(follower.id);
        }
    }

    private synchronized void advanceCommitIndex() {
        // find N such that majority has replicated N
        long last = lastLogIndex();
        long quorum = peerSet.quorumSize();

        for (long n = last; n > commitIndex; n--) {
            long count = 1; // leader itself
            for (Peer p : peerSet.others(selfId())) {
                long mi = matchIndex.getOrDefault(p.id, 0L);
                if (mi >= n) count++;
            }
            if (count >= quorum && logTermAt(n) == currentTerm) {
                commitIndex = n;
                break;
            }
        }
    }

    private synchronized void applyCommitted() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry e = log.get((int) lastApplied);

            if ("PAYMENT".equals(e.commandType)) {
                try {
                    PaymentRequest pr = objectMapper.readValue(e.commandJson, PaymentRequest.class);

                    LedgerEntry le = new LedgerEntry();
                    le.logIndex = e.index;
                    le.from = pr.from;
                    le.to = pr.to;
                    le.amount = pr.amount;
                    le.idempotencyKey = pr.idempotencyKey;
                    le.clientTimestampMs = e.clientTimestampMs;
                    le.correctedTimestampMs = e.clientTimestampMs + timeOffsetEstimator.currentOffsetMs();

                    // dedup at state machine level
                    ledgerService.stateMachine().apply(le);
                } catch (Exception ignored) { }
            }
        }
    }

    // Client submit entry (leader only)
    public synchronized long submitPayment(PaymentRequest req) throws Exception {
        if (role != RaftRole.LEADER) {
            throw new IllegalStateException("NOT_LEADER");
        }

        // dedup early (fast path)
        if (ledgerService.stateMachine().hasProcessed(req.idempotencyKey)) {
            return -1;
        }

        // mock process
        boolean ok = paymentProcessor.process(req);
        if (!ok) {
            // For prototype: still record failed if needed; keeping simple
        }

        LogEntry e = new LogEntry();
        e.index = lastLogIndex() + 1;
        e.term = currentTerm;
        e.commandType = "PAYMENT";
        e.commandJson = objectMapper.writeValueAsString(req);
        e.idempotencyKey = req.idempotencyKey;
        e.clientTimestampMs = System.currentTimeMillis();

        log.add(e);
        return e.index;
    }
}
# Distributed Systems Group Assignment
## Designing a Fault-Tolerant Distributed Payment Processing System (Java + Spring Boot + Self-Implemented Raft)

**Current date referenced by user:** 2026-03-29

This document is a practical build + run plan for a prototype distributed payment processing system that:
- runs **multiple payment server nodes** concurrently on **one machine** (different ports),
- remains available under node failures via **Raft leader election + replication**,
- provides a replicated **payment ledger** that can be queried from any node,
- prevents duplicates using **idempotency keys (deduplication)**,
- adds **time ordering** using physical timestamps + a logical clock (HLC or Lamport),
- uses a **mock payment processor** (no Stripe/PayPal required),
- does **not require ZooKeeper**.

---

## 1) High-Level Architecture (What we are building)

### Components
1. **Client (Load test / CLI)**
   - Sends `POST /pay` concurrently to any node
   - Retries on failure/timeouts
   - Uses a unique `idempotencyKey` per logical payment attempt to avoid duplicates

2. **Payment Server Node (3 nodes recommended)**
   - Spring Boot application exposing:
     - Client-facing API: `/pay`, `/ledger`, `/health`
     - Raft internal RPC: `/raft/requestVote`, `/raft/appendEntries`
   - Runs as a separate OS process per node on different ports (e.g., 8001/8002/8003)

3. **Raft Consensus / Replicated Log**
   - Each node stores an append-only log
   - Leader replicates log entries to followers
   - Entry is committed when a **majority** acknowledges it
   - Ledger is built by applying committed entries to a state machine

4. **Ledger State Machine**
   - Applies committed log entries to:
     - a ledger list / map (payment history)
     - an idempotency/dedup index: `idempotencyKey -> PaymentResult`

5. **Time and Ordering**
   - Each log entry contains:
     - physical timestamp: `System.currentTimeMillis()`
     - logical timestamp: Hybrid Logical Clock (HLC) or Lamport clock
   - Queries/log displays can reorder safely using logical time

---

## 2) Why ZooKeeper is NOT needed
ZooKeeper is an external coordination service often used for leader election or configuration.  
In this project we implement **Raft** ourselves, which already provides:
- leader election
- failure detection via heartbeat timeouts
- automatic failover
- log replication and catch-up

Therefore **no ZooKeeper** is required for this prototype.

---

## 3) Technology Choices (Java, Spring Boot, One Machine)
### Language & Framework
- Java **17** recommended
- Spring Boot (REST endpoints)

### Build Tool
- Maven

### Storage
- Simple file-based persistence:
  - persistent term/votedFor
  - append-only Raft log in JSON lines
- In-memory maps built from the log on startup

### Mock Payment Processor
- A deterministic/random mock that can simulate:
  - success/failure
  - delay
  - timeouts (optional)
- Used to test retries + deduplication + failover

---

## 4) Repository Layout (Suggested)
```
distributed-payments-java/
  README.md
  PROJECT_PLAN.md
  links.txt                    # submission links (GitHub repo, YouTube video)
  pom.xml
  config/
    node1.properties
    node2.properties
    node3.properties
  scripts/
    run-cluster.sh
    run-cluster.ps1
    stop-cluster.sh            # optional
    load-test.sh               # optional
  src/main/java/com/example/payments/
    Application.java
    config/
      NodeConfig.java
    api/
      PaymentController.java
      LedgerController.java
      HealthController.java
    raft/
      RaftNode.java
      RaftRole.java
      RaftRpcController.java
      RaftScheduler.java
      RaftLog.java
      LogEntry.java
      RequestVoteRequest.java
      RequestVoteResponse.java
      AppendEntriesRequest.java
      AppendEntriesResponse.java
      PersistentStateStore.java
    ledger/
      LedgerStateMachine.java
      LedgerStore.java
      DedupStore.java
    payment/
      MockPaymentProcessor.java
      PaymentRequest.java
      PaymentResult.java
    time/
      HybridLogicalClock.java   # or LamportClock.java
      Timestamp.java
  src/test/java/... (optional)
```

---

## 5) Configuration: Running multiple nodes on one machine
You build **one JAR**, then run it multiple times with different config files.

### Example: `config/node1.properties`
```properties
# Spring Boot server port
server.port=8001

# Node identity
node.id=n1
node.host=127.0.0.1
node.port=8001
node.dataDir=data/n1

# Cluster peers (other nodes)
cluster.peers=http://127.0.0.1:8002,http://127.0.0.1:8003

# Raft timings (tune if needed)
raft.electionTimeoutMinMs=300
raft.electionTimeoutMaxMs=600
raft.heartbeatIntervalMs=120
```

### `config/node2.properties` (example)
```properties
server.port=8002
node.id=n2
node.host=127.0.0.1
node.port=8002
node.dataDir=data/n2
cluster.peers=http://127.0.0.1:8001,http://127.0.0.1:8003
raft.electionTimeoutMinMs=300
raft.electionTimeoutMaxMs=600
raft.heartbeatIntervalMs=120
```

### `config/node3.properties` (example)
```properties
server.port=8003
node.id=n3
node.host=127.0.0.1
node.port=8003
node.dataDir=data/n3
cluster.peers=http://127.0.0.1:8001,http://127.0.0.1:8002
raft.electionTimeoutMinMs=300
raft.electionTimeoutMaxMs=600
raft.heartbeatIntervalMs=120
```

---

## 6) How to Build and Run (No Docker required)
### Prerequisites
- JDK 17 installed
- Maven installed

### Build
```bash
mvn -DskipTests package
```

### Run nodes (3 terminals)
```bash
java -jar target/<YOUR_APP_NAME>.jar --spring.config.additional-location=file:config/node1.properties
java -jar target/<YOUR_APP_NAME>.jar --spring.config.additional-location=file:config/node2.properties
java -jar target/<YOUR_APP_NAME>.jar --spring.config.additional-location=file:config/node3.properties
```

> Replace `<YOUR_APP_NAME>` with your actual jar name produced by Maven (visible in `target/`).

---

## 7) API Design (Endpoints)

### 7.1 Client-Facing APIs
#### `POST /pay`
Request JSON:
```json
{
  "idempotencyKey": "uuid-generated-by-client",
  "userId": "u123",
  "orderId": "o789",
  "amountCents": 4999,
  "currency": "USD"
}
```

Behavior:
- If node is **leader**:
  1) check dedup store by `idempotencyKey`
  2) if not exists: run MockPaymentProcessor
  3) create log entry with payment result
  4) replicate via Raft; wait for majority commit
  5) apply to ledger state machine
  6) return result
- If node is **not leader**:
  - return an error that includes leader info (implementation choice can be decided later):
    - option A: HTTP redirect (307) with leader URL
    - option B: return 409/503 with `leaderHint` in JSON
    - option C: follower forwards request to leader internally

Response JSON (example):
```json
{
  "paymentId": "p-00000042",
  "status": "SUCCESS",
  "message": "approved",
  "leaderHint": "http://127.0.0.1:8001",
  "timestamp": {
    "physicalMs": 1760000000000,
    "hlc": "1760000000000:5:n1"
  }
}
```

#### `GET /ledger`
Query options (choose one approach):
- `GET /ledger?userId=u123`
- `GET /ledger?orderId=o789`
- `GET /ledger` returns all (for demo/testing)

Returns list of committed ledger entries.

#### `GET /health`
Returns node status:
```json
{
  "nodeId": "n2",
  "role": "FOLLOWER",
  "currentTerm": 7,
  "leaderId": "n1",
  "commitIndex": 42,
  "lastApplied": 42
}
```

---

### 7.2 Raft Internal RPC APIs
These endpoints are called node-to-node.

#### `POST /raft/requestVote`
Request fields:
- `term`
- `candidateId`
- `lastLogIndex`
- `lastLogTerm`

Response:
- `term` (for term updates)
- `voteGranted` (boolean)

#### `POST /raft/appendEntries`
Request fields:
- `term`
- `leaderId`
- `prevLogIndex`
- `prevLogTerm`
- `entries[]` (can be empty for heartbeat)
- `leaderCommit`

Response:
- `term`
- `success` (boolean)
- (optional optimization) `conflictIndex` / `conflictTerm` for faster backtracking

---

## 8) Raft Implementation Scope (Prototype but real behavior)
### Node Roles
- FOLLOWER
- CANDIDATE
- LEADER

### Timers
- Election timeout: randomized per node, e.g. 300–600ms
- Heartbeat interval: e.g. 120ms (leader sends empty AppendEntries)

### Leader Election
- follower times out -> candidate
- increments term
- votes for itself
- sends RequestVote to peers
- wins majority -> leader

### Log Replication
- leader appends new entry to its log
- sends AppendEntries to followers
- followers validate `prevLogIndex/prevLogTerm`, append entries
- leader tracks acks; when majority replicated, commit

### Commit and Apply
- leader advances `commitIndex`
- each node applies entries `[lastApplied+1 .. commitIndex]` to the ledger state machine

### Recovery / Rejoin
- node restarts, loads `currentTerm`, `votedFor`, log
- follower catches up from leader via AppendEntries

---

## 9) Deduplication / Idempotency (Required for retries and failover)
### Requirement
Prevent double-recording if:
- client retries due to timeout,
- follower redirects to leader,
- leader crashes after processing but before responding.

### Mechanism
- Client always includes `idempotencyKey`.
- Log entry includes the same `idempotencyKey`.
- State machine stores:
  - `idempotencyKey -> PaymentResult`
- When leader receives a `/pay`:
  - if key exists: return saved result immediately
  - else process + replicate new entry

This is a standard approach used in real payment systems.

---

## 10) Time Synchronization & Out-of-Order Logs
Even on one machine, you implement a robust scheme.

### Physical timestamps
- Use `System.currentTimeMillis()` in each node.

### Logical ordering (recommended)
- Implement **Hybrid Logical Clock (HLC)** or Lamport clock.
- Include it in each log entry.
- When viewing logs, sort by:
  - `(hlc.physicalMs, hlc.logicalCounter, nodeId)`

### In report
- Mention that on multi-machine deployments, NTP is used for improved clock sync.
- Explain how clock skew can affect ordering and how HLC mitigates it.

---

## 11) Failure Scenarios to Demonstrate (Tests)
1. **Leader crash**
   - start cluster
   - identify leader from `/health`
   - kill leader process
   - observe new leader elected
   - client can continue by sending to remaining nodes

2. **Follower crash and rejoin**
   - kill follower
   - process payments
   - restart follower
   - follower catches up and ledger matches leader

3. **Network delay simulation (optional)**
   - add artificial sleep in Raft RPC client calls (config-controlled)
   - show increased latency but correct commits

---

## 12) Performance / Evaluation Metrics (for report + presentation)
Measure for different loads (e.g., 10, 50, 100 concurrent requests):
- throughput (requests/sec)
- average latency (ms)
- p95 latency (ms)
- storage overhead: log replicated across N nodes (approx N×)
- effect of redundancy: commit requires majority ack -> additional RTT

---

## 13) Task Division Across 4 Members (Recommended)
### Member 1: Fault Tolerance
- Health endpoint + cluster run scripts
- failure injection (kill/restart nodes)
- document recovery behavior + overhead evaluation
- client retry logic and demonstration plan

### Member 2: Data Replication & Consistency
- log entry model + state machine apply
- dedup/idempotency store derived from log
- ledger query API and correctness checks
- latency/storage analysis due to replication

### Member 3: Time Synchronization
- implement HLC (or Lamport) + timestamp struct
- reorder/display logic for logs
- analyze clock skew, ordering, and overhead in report

### Member 4: Consensus & Agreement (Raft)
- RequestVote/AppendEntries RPCs
- election + heartbeats + leader state
- commit logic and basic optimizations
- test under node crash scenarios

---

## 14) Deliverables Checklist
### Report (10–12 pages)
Include:
- architecture diagram and explanation
- Raft replication + consistency model trade-offs
- fault tolerance behavior and recovery
- deduplication strategy
- time ordering strategy (HLC/Lamport + NTP mention)
- experiments: throughput/latency under load and failures

### Implementation
- working cluster of 3 nodes
- can process concurrent payments
- ledger query works from any node
- survives node failures (leader crash)

### Presentation (15 mins) + Video
- show architecture
- demo failover
- show dedup (same idempotencyKey -> same result)
- show ledger consistency (same ledger on all nodes after catch-up)

### Code + Git History
- commit from day 1
- descriptive commits
- provide GitHub link in `links.txt`

---

## 15) Notes on “Redirect vs Forward” (Can decide later)
Follower behavior when receiving `/pay`:
- **Redirect**: simplest; client retries to leader (good enough for prototype)
- **Forward**: follower acts like proxy; easier for client, more code

Decision can be

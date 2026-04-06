# How to Run — Distributed Payments (Raft-based)

This project is a prototype **Fault-Tolerant Distributed Payment Processing System** built with **Java 21 + Spring Boot (Maven)**.  
It runs as a **3-node cluster** using **Raft** (leader election + log replication) to maintain a consistent replicated payment ledger.

## Prerequisites
- Java **21** installed (`java -version`)
- Git (optional)
- Internet not required (runs locally)
- OS: Windows/macOS/Linux

## Project Layout Assumptions
- Maven wrapper is present (`mvnw`, `mvnw.cmd`)
- Node configs exist:
  - `src/main/resources/config/node1.properties`
  - `src/main/resources/config/node2.properties`
  - `src/main/resources/config/node3.properties`

## 1) Build / Verify Compile
From the project root (where `pom.xml` exists):

### macOS/Linux
```bash
./mvnw clean test
```

### Windows (PowerShell / CMD)
```bat
mvnw.cmd clean test
```

If this succeeds, the project compiles correctly.

## 2) Start the 3 Nodes (3 separate terminals)
Each node is the same Spring Boot app, started with a different config file.

> Keep all 3 nodes running at the same time.

### Node 1 (port 8081)
**macOS/Linux**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:/config/node1.properties"
```

**Windows**
```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:/config/node1.properties"
```

### Node 2 (port 8082)
**macOS/Linux**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:/config/node2.properties"
```

**Windows**
```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:/config/node2.properties"
```

### Node 3 (port 8083)
**macOS/Linux**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:/config/node3.properties"
```

**Windows**
```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:/config/node3.properties"
```

## 3) Confirm the Cluster + Find the Leader
After starting all nodes, wait ~5–10 seconds for Raft leader election.

Check each node:
```bash
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```

Look for the node with:
- `"role": "LEADER"`

That node is the current leader.

## 4) Submit a Payment
Send payments to the leader node. Example (leader assumed to be `8081`):

```bash
curl -X POST http://localhost:8081/pay \
  -H "Content-Type: application/json" \
  -d '{
    "from": "alice",
    "to": "bob",
    "amount": 10.50,
    "idempotencyKey": "pay-001"
  }'
```

### If you send to a follower
You may get a response like:
- HTTP `409` with status `REDIRECT`
- A `leaderUrl` field indicating where to resend the request

## 5) Verify Ledger Replication
You can read the ledger from **any node**:

```bash
curl http://localhost:8081/ledger
curl http://localhost:8082/ledger
curl http://localhost:8083/ledger
```

They should converge to the same ordered entries (after replication/commit).

## 6) Check Balances
```bash
curl http://localhost:8081/balance/alice
curl http://localhost:8081/balance/bob
```

(You can query balances on any node.)

## 7) Deduplication Test (Retries / Failover)
Re-send the same payment with the same `idempotencyKey`:

```bash
curl -X POST http://localhost:8081/pay \
  -H "Content-Type: application/json" \
  -d '{
    "from": "alice",
    "to": "bob",
    "amount": 10.50,
    "idempotencyKey": "pay-001"
  }'
```

Expected behavior:
- The duplicate request should be ignored (deduplicated) and should not create a second ledger entry.

## 8) Failover Test (Leader Crash)
1. Identify the leader using `/health`.
2. Stop the leader process (Ctrl+C in that terminal).
3. Wait ~5–10 seconds.
4. Check `/health` on the remaining nodes:
   - A new node should become `"LEADER"`.
5. Submit new payments to the new leader.

## 9) Rejoin / Recovery Test
Restart the stopped node using its same command (node1/node2/node3 config).  
It should rejoin and catch up (via AppendEntries replication), then its `/ledger` should match the cluster.

---

## Troubleshooting

### Ports already in use
If `8081/8082/8083` are taken, change ports in:
- `src/main/resources/config/node1.properties`
- `src/main/resources/config/node2.properties`
- `src/main/resources/config/node3.properties`

Make sure `cluster.peers` URLs match the updated ports.

### Maven dependencies not downloading / build fails
Run:
```bash
./mvnw -U clean test
```

### Leader not elected
- Ensure all nodes can reach each other at the URLs listed in `cluster.peers`.
- Ensure each node has a unique `node.id` and correct `node.url`.

---

## Quick Demo Script (for presentation)
1. Start 3 nodes
2. Use `/health` to show leader
3. `POST /pay` to leader
4. `GET /ledger` on all 3 nodes (show same replicated data)
5. Kill leader, show new leader elected
6. Submit another payment to new leader
7. Restart old leader, show it catches up
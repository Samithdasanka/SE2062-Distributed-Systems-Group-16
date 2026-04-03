package com.group16.distributed.payments.api;

import com.group16.distributed.payments.ledger.LedgerService;
import com.group16.distributed.payments.payment.PaymentRequest;
import com.group16.distributed.payments.payment.PaymentResponse;
import com.group16.distributed.payments.raft.RaftNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {

    private final RaftNode raftNode;
    private final LedgerService ledgerService;

    public PaymentController(RaftNode raftNode, LedgerService ledgerService) {
        this.raftNode = raftNode;
        this.ledgerService = ledgerService;
    }

    @PostMapping("/pay")
    public ResponseEntity<PaymentResponse> pay(@RequestBody PaymentRequest req) {
        PaymentResponse resp = new PaymentResponse();
        resp.idempotencyKey = req.idempotencyKey;

        // if already processed, return committed
        if (ledgerService.stateMachine().hasProcessed(req.idempotencyKey)) {
            resp.status = "COMMITTED";
            resp.message = "Duplicate request ignored (dedup). Already committed.";
            resp.logIndex = -1;
            return ResponseEntity.ok(resp);
        }

        if (raftNode.role().name().equals("LEADER")) {
            try {
                long idx = raftNode.submitPayment(req);
                resp.status = "PENDING";
                resp.message = "Accepted by leader. Will commit after replication quorum.";
                resp.logIndex = idx;
                return ResponseEntity.ok(resp);
            } catch (Exception e) {
                resp.status = "REJECTED";
                resp.message = "Leader failed to accept payment: " + e.getMessage();
                return ResponseEntity.status(500).body(resp);
            }
        }

        // not leader -> redirect info
        resp.status = "REDIRECT";
        resp.message = "Not leader. Send request to leader.";
        resp.leaderUrl = raftNode.leaderUrl();
        resp.logIndex = 0;
        return ResponseEntity.status(409).body(resp);
    }

    @GetMapping("/ledger")
    public Object ledger() {
        return ledgerService.stateMachine().getEntries();
    }

    @GetMapping("/balance/{account}")
    public Object balance(@PathVariable String account) {
        return ledgerService.stateMachine().getBalance(account);
    }
}
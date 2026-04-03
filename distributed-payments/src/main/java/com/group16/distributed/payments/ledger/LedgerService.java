package com.group16.distributed.payments.ledger;

import org.springframework.stereotype.Service;

@Service
public class LedgerService {
    private final LedgerStateMachine stateMachine = new LedgerStateMachine();

    public LedgerStateMachine stateMachine() {
        return stateMachine;
    }
}
package com.group16.distributed.payments.payment;

public class PaymentResponse {
    public String status; // COMMITTED, PENDING, REDIRECT, REJECTED
    public String message;
    public String leaderUrl;
    public String idempotencyKey;
    public long logIndex;
}
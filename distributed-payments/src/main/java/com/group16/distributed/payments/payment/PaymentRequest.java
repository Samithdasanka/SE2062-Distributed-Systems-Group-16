package com.group16.distributed.payments.payment;

import java.math.BigDecimal;

public class PaymentRequest {
    public String from;
    public String to;
    public BigDecimal amount;
    public String idempotencyKey;
}
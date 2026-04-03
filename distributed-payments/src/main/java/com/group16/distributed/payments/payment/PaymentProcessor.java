package com.group16.distributed.payments.payment;

import org.springframework.stereotype.Component;

@Component
public class PaymentProcessor {
    public boolean process(PaymentRequest req) {
        return true;
    }
}
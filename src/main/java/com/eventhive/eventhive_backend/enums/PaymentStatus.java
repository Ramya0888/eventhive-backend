package com.eventhive.eventhive_backend.enums;

public enum PaymentStatus {
    CREATED,    // order created, awaiting payment
    SUCCESS,    // payment verified
    FAILED      // payment failed or signature mismatch
}
package com.eventhive.eventhive_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Frontend sends these three values back after the Razorpay
 * checkout completes. The backend uses them to verify the signature.
 */
@Getter
@Setter
public class VerifyPaymentRequest {

    @NotBlank
    private String razorpayOrderId;

    @NotBlank
    private String razorpayPaymentId;

    @NotBlank
    private String razorpaySignature;

    @NotBlank
    private String bookingReference;
}
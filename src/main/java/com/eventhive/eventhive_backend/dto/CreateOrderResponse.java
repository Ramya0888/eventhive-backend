package com.eventhive.eventhive_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * Returned to the frontend after creating a Razorpay order.
 * The frontend uses these values to open the Razorpay checkout popup.
 */
@Getter
@Builder
public class CreateOrderResponse {
    private String razorpayOrderId;
    private BigDecimal amount;
    private String currency;
    private String bookingReference;
    private String keyId;   
}
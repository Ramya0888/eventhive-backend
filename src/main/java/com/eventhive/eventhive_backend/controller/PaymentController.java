package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.dto.BookingResponse;
import com.eventhive.eventhive_backend.dto.CreateOrderResponse;
import com.eventhive.eventhive_backend.dto.VerifyPaymentRequest;
import com.eventhive.eventhive_backend.security.CustomUserDetails;
import com.eventhive.eventhive_backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/payments/create-order
     * Step 1: create a Razorpay order for a PENDING booking.
     */
    @PostMapping("/create-order")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @RequestParam String bookingReference,
            @AuthenticationPrincipal CustomUserDetails principal) {

        CreateOrderResponse response = paymentService.createOrder(
                bookingReference, principal.getUser().getEmail());
        return ResponseEntity.ok(
                ApiResponse.success("Order created", response));
    }

    /**
     * POST /api/payments/verify
     * Step 3: verify Razorpay signature, confirm booking.
     */
    @PostMapping("/verify")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<ApiResponse<BookingResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        BookingResponse response = paymentService.verifyPayment(request);
        return ResponseEntity.ok(
                ApiResponse.success("Payment verified — booking confirmed", response));
    }
}
package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.BookingResponse;
import com.eventhive.eventhive_backend.dto.CreateOrderResponse;
import com.eventhive.eventhive_backend.dto.VerifyPaymentRequest;
import com.eventhive.eventhive_backend.entity.Booking;
import com.eventhive.eventhive_backend.entity.BookingItem;
import com.eventhive.eventhive_backend.entity.Payment;
import com.eventhive.eventhive_backend.entity.Seat;
import com.eventhive.eventhive_backend.enums.BookingStatus;
import com.eventhive.eventhive_backend.enums.PaymentStatus;
import com.eventhive.eventhive_backend.enums.SeatStatus;
import com.eventhive.eventhive_backend.exception.AppException;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.BookingItemRepository;
import com.eventhive.eventhive_backend.repository.BookingRepository;
import com.eventhive.eventhive_backend.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
    }

    @Transactional
    public CreateOrderResponse createOrder(String bookingReference, String userEmail) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + bookingReference));

        // Ownership check
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingReference);
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new AppException(
                    "Payment only allowed for PENDING bookings. Status: "
                    + booking.getStatus(), HttpStatus.CONFLICT);
        }

        // Check booking hasn't expired
        if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(
                    "Booking has expired. Please select seats again.",
                    HttpStatus.GONE);   // 410 Gone — resource existed but is now gone
        }

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            // Razorpay requires amount in smallest currency unit (paise).
            // multiply by 100: ₹1000 → 100000 paise.
            long amountInPaise = booking.getTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", booking.getBookingReference());

            Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            // Persist payment record in CREATED state
            Payment payment = Payment.builder()
                    .booking(booking)
                    .razorpayOrderId(razorpayOrderId)
                    .amount(booking.getTotalAmount())
                    .status(PaymentStatus.CREATED)
                    .build();
            paymentRepository.save(payment);

            log.info("Razorpay order {} created for booking {}",
                    razorpayOrderId, bookingReference);

            return CreateOrderResponse.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .amount(booking.getTotalAmount())
                    .currency("INR")
                    .bookingReference(bookingReference)
                    .keyId(keyId)   // public key — safe to send to client
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new AppException("Payment gateway error. Please try again.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

   
    @Transactional
    public BookingResponse verifyPayment(VerifyPaymentRequest request) {
        Payment payment = paymentRepository
                .findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment record not found for order: "
                        + request.getRazorpayOrderId()));

        Booking booking = payment.getBooking();

        // Verify signature
        boolean isValid = verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        if (!isValid) {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Invalid payment signature for order {}",
                    request.getRazorpayOrderId());
            throw new AppException("Payment verification failed — invalid signature.",
                    HttpStatus.BAD_REQUEST);
        }

        // Valid payment — update payment record
        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus(PaymentStatus.SUCCESS);

        // Confirm the booking
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        // Permanently book all seats (RESERVED → BOOKED)
        List<BookingItem> items = bookingItemRepository
                .findByBookingId(booking.getId());
        items.forEach(item -> item.getSeat().setStatus(SeatStatus.BOOKED));

        log.info("Payment verified for booking {} — {} seats BOOKED",
                booking.getBookingReference(), items.size());

        return BookingResponse.from(booking);
    }

   
    private boolean verifySignature(String orderId, String paymentId,
                                    String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
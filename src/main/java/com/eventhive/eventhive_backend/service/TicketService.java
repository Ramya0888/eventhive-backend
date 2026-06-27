package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.entity.Booking;
import com.eventhive.eventhive_backend.entity.BookingItem;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class TicketService {

    private final JavaMailSender mailSender;
   
    
    private final TicketDataLoader ticketDataLoader;
    @Value("${spring.mail.username}")
    private String fromEmail;

    public TicketService(JavaMailSender mailSender,
                         
                      
                         TicketDataLoader ticketDataLoader) {
        this.mailSender = mailSender;
     
        this.ticketDataLoader = ticketDataLoader;
    }


    @Async
    public void generateAndSendTicket(Booking booking) {
        log.info("Generating ticket for booking {} (async thread: {})",
                booking.getBookingReference(),
                Thread.currentThread().getName());
        try {
            // Load all data in one @Transactional call while session is open,
            // extract plain values. No entity references leave this method.
            TicketData data = ticketDataLoader.load(booking.getId());

            byte[] qrBytes  = generateQrCode(data.bookingReference);
            byte[] pdfBytes = generatePdf(data, qrBytes);
            sendTicketEmail(data, pdfBytes);
    
            log.info("Ticket sent successfully for booking {}", data.bookingReference);
    
        } catch (Exception e) {
            log.error("Failed to send ticket for booking {}: {}",
                    booking.getBookingReference(), e.getMessage());
        }
    }
    
public static class TicketData {
    public final String bookingReference;
    public final String eventTitle;
    public final String eventDate;
    public final String venueName;
    public final String venueCity;
    public final String attendeeName;
    public final String attendeeEmail;
    public final java.math.BigDecimal totalAmount;
    public final List<String> seatLines;

    public TicketData(String bookingReference, String eventTitle,
                      String eventDate, String venueName, String venueCity,
                      String attendeeName, String attendeeEmail,
                      java.math.BigDecimal totalAmount, List<String> seatLines) {
        this.bookingReference = bookingReference;
        this.eventTitle       = eventTitle;
        this.eventDate        = eventDate;
        this.venueName        = venueName;
        this.venueCity        = venueCity;
        this.attendeeName     = attendeeName;
        this.attendeeEmail    = attendeeEmail;
        this.totalAmount      = totalAmount;
        this.seatLines        = seatLines;
    }
}

    private byte[] generateQrCode(String bookingReference)
            throws WriterException, IOException {

        QRCodeWriter writer = new QRCodeWriter();

        // Encode the booking reference into a 200x200 QR code
        BitMatrix bitMatrix = writer.encode(
                bookingReference,
                BarcodeFormat.QR_CODE,
                200, 200);

        // Convert BitMatrix → BufferedImage → PNG bytes
        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

   
    private byte[] generatePdf(TicketData data, byte[] qrBytes)
        throws DocumentException, IOException {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Document document = new Document();
    PdfWriter.getInstance(document, baos);
    document.open();

    Font titleFont = new Font(Font.FontFamily.HELVETICA, 22,
            Font.BOLD, BaseColor.DARK_GRAY);
    Paragraph title = new Paragraph("EventHive — E-Ticket", titleFont);
    title.setAlignment(Element.ALIGN_CENTER);
    title.setSpacingAfter(20);
    document.add(title);

    Font headingFont = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD);
    Font bodyFont    = new Font(Font.FontFamily.HELVETICA, 11);

    document.add(new Paragraph("Event Details", headingFont));
    document.add(new Paragraph("Event    : " + data.eventTitle, bodyFont));
    document.add(new Paragraph("Date     : " + data.eventDate, bodyFont));
    document.add(new Paragraph("Venue    : " + data.venueName
            + ", " + data.venueCity, bodyFont));
    document.add(Chunk.NEWLINE);

    document.add(new Paragraph("Booking Details", headingFont));
    document.add(new Paragraph("Reference : " + data.bookingReference, bodyFont));
    document.add(new Paragraph("Attendee  : " + data.attendeeName, bodyFont));
    document.add(new Paragraph("Amount    : INR " + data.totalAmount, bodyFont));
    document.add(Chunk.NEWLINE);

    document.add(new Paragraph("Seats", headingFont));
    for (String seatLine : data.seatLines) {
        document.add(new Paragraph("  • " + seatLine, bodyFont));
    }
    document.add(Chunk.NEWLINE);

    document.add(new Paragraph("Scan at venue entry", headingFont));
    Image qrImage = Image.getInstance(qrBytes);
    qrImage.setAlignment(Element.ALIGN_CENTER);
    qrImage.scaleToFit(150, 150);
    document.add(qrImage);
    document.add(Chunk.NEWLINE);

    Font footerFont = new Font(Font.FontFamily.HELVETICA, 9,
            Font.ITALIC, BaseColor.GRAY);
    Paragraph footer = new Paragraph(
            "This is your official ticket. Please carry this to the event.",
            footerFont);
    footer.setAlignment(Element.ALIGN_CENTER);
    document.add(footer);

    document.close();
    return baos.toByteArray();
}

private void sendTicketEmail(TicketData data, byte[] pdfBytes)
        throws MessagingException {

    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

    helper.setFrom(fromEmail);
    helper.setTo(data.attendeeEmail);
    helper.setSubject("Your EventHive Ticket — " + data.bookingReference);

    helper.setText(
            "Hi " + data.attendeeName + ",\n\n"
            + "Your booking is confirmed! Please find your e-ticket attached.\n\n"
            + "Event    : " + data.eventTitle + "\n"
            + "Date     : " + data.eventDate + "\n"
            + "Reference: " + data.bookingReference + "\n"
            + "Amount   : INR " + data.totalAmount + "\n\n"
            + "Please carry this ticket to the venue.\n\n"
            + "— EventHive Team",
            false
    );

    helper.addAttachment(
            "EventHive-Ticket-" + data.bookingReference + ".pdf",
            new ByteArrayResource(pdfBytes)
    );

    mailSender.send(message);
    log.info("Ticket email sent to {}", data.attendeeEmail);
}
}
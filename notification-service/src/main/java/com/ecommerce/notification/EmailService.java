package com.ecommerce.notification;

import com.ecommerce.notification.dto.LowStockAlertRequest;
import com.ecommerce.notification.dto.OrderCancellationRequest;
import com.ecommerce.notification.dto.OrderConfirmationRequest;
import com.ecommerce.notification.dto.WelcomeRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;

/**
 * Same @Async reasoning as the monolith (ADR B-11) — the HTTP call that
 * triggers this (from order-service, once it exists) should return
 * immediately; the actual SMTP round-trip (200ms-3000ms) happens in the
 * background thread pool defined in AsyncConfig.
 *
 * The one real change from the monolith version: every method here used to
 * take a `User` and/or `Order` JPA entity. Those entities don't exist in
 * this service's classpath anymore — it has no database, no idea what an
 * Order looks like internally. The caller now does the assembly and sends
 * a flat DTO instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${mail.from.address:programmer143143@gmail.com}")
    private String fromAddress;

    @Value("${mail.from.name:EcommerceApp}")
    private String fromName;

    @Async
    public void sendOrderConfirmation(OrderConfirmationRequest req) {
        try {
            Context ctx = new Context();
            ctx.setVariable("firstName", req.firstName());
            ctx.setVariable("orderId", req.orderId());
            ctx.setVariable("status", req.status());
            ctx.setVariable("totalPrice", req.totalPrice());
            ctx.setVariable("items", req.items());
            ctx.setVariable("placedAt",
                    req.placedAt() == null ? "-" :
                            req.placedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));

            String htmlBody = templateEngine.process("emails/order-confirmation", ctx);
            sendHtmlEmail(req.recipientEmail(),
                    "Order Confirmed #" + req.orderId() + " — EcommerceApp",
                    htmlBody);

            log.info("Order confirmation email sent to {} for order #{}", req.recipientEmail(), req.orderId());
        } catch (Exception e) {
            log.error("Failed to send order confirmation to {} for order #{}: {}",
                    req.recipientEmail(), req.orderId(), e.getMessage());
        }
    }

    @Async
    public void sendOrderCancellation(OrderCancellationRequest req) {
        try {
            Context ctx = new Context();
            ctx.setVariable("firstName", req.firstName());
            ctx.setVariable("orderId", req.orderId());
            ctx.setVariable("totalPrice", req.totalPrice());

            String htmlBody = templateEngine.process("emails/order-cancellation", ctx);
            sendHtmlEmail(req.recipientEmail(),
                    "Order Cancelled #" + req.orderId() + " — EcommerceApp",
                    htmlBody);

            log.info("Cancellation email sent to {} for order #{}", req.recipientEmail(), req.orderId());
        } catch (Exception e) {
            log.error("Failed to send cancellation email to {}: {}", req.recipientEmail(), e.getMessage());
        }
    }

    @Async
    public void sendWelcome(WelcomeRequest req) {
        try {
            Context ctx = new Context();
            ctx.setVariable("firstName", req.firstName());
            ctx.setVariable("email", req.recipientEmail());

            String htmlBody = templateEngine.process("emails/welcome", ctx);
            sendHtmlEmail(req.recipientEmail(), "Welcome to EcommerceApp!", htmlBody);

            log.info("Welcome email sent to {}", req.recipientEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", req.recipientEmail(), e.getMessage());
        }
    }

    @Async
    public void sendLowStockAlert(LowStockAlertRequest req) {
        try {
            String html = "<h2>Low Stock Alert</h2>"
                    + "<p>Product <strong>" + req.productName() + "</strong> has only "
                    + "<strong>" + req.remainingStock() + "</strong> units remaining.</p>"
                    + "<p>Please restock soon.</p>";

            sendHtmlEmail(fromAddress, "Low Stock Alert: " + req.productName(), html);
            log.info("Low stock alert sent for {}", req.productName());
        } catch (Exception e) {
            log.error("Failed to send low stock alert: {}", e.getMessage());
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        try {
            helper.setFrom(fromAddress, fromName);
        } catch (java.io.UnsupportedEncodingException e) {
            helper.setFrom(fromAddress);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }
}

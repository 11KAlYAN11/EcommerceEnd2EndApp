package com.ecommerce.notification;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderItem;
import com.ecommerce.user.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * WHY @Async ON EMAIL METHODS?
 *
 * Email sending involves:
 *   - TCP connection to Gmail's SMTP server
 *   - SMTP handshake (EHLO, STARTTLS, AUTH)
 *   - Actual email transfer
 *   Total: 200ms – 3000ms
 *
 * Without @Async:
 *   POST /api/orders → placeOrder() → sendOrderConfirmation() → wait 1-2s for Gmail → return response
 *   User stares at "loading" for 2 extra seconds every order
 *
 * With @Async:
 *   POST /api/orders → placeOrder() → order created → return 201 immediately
 *                                   → sendOrderConfirmation() runs in background thread
 *   User gets instant response. Email arrives in their inbox a second later.
 *
 * @Async works via Spring AOP — the method call is intercepted and submitted
 * to a thread pool. The caller gets back immediately.
 * @EnableAsync must be on a config class.
 *
 * WHY TRY-CATCH around sending?
 *   Email failure must NEVER fail the order.
 *   The order is committed before email is sent (@Async runs after return).
 *   If Gmail is down: order still exists, email just doesn't arrive.
 *   Log the error, potentially retry later (Phase 17: Kafka dead-letter queue).
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

    // ── Order Confirmation ────────────────────────────────────────────────────

    @Async
    public void sendOrderConfirmation(User user, Order order) {
        try {
            Context ctx = new Context();
            ctx.setVariable("firstName", user.getFirstName());
            ctx.setVariable("orderId", order.getId());
            ctx.setVariable("status", order.getStatus().name());
            ctx.setVariable("totalPrice", order.getTotalPrice());
            ctx.setVariable("items", order.getItems());
            ctx.setVariable("placedAt",
                    order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));

            String htmlBody = templateEngine.process("emails/order-confirmation", ctx);
            sendHtmlEmail(user.getEmail(),
                    "Order Confirmed #" + order.getId() + " — EcommerceApp",
                    htmlBody);

            log.info("Order confirmation email sent to {} for order #{}", user.getEmail(), order.getId());
        } catch (Exception e) {
            log.error("Failed to send order confirmation to {} for order #{}: {}",
                    user.getEmail(), order.getId(), e.getMessage());
        }
    }

    // ── Order Cancellation ────────────────────────────────────────────────────

    @Async
    public void sendOrderCancellation(User user, Order order) {
        try {
            Context ctx = new Context();
            ctx.setVariable("firstName", user.getFirstName());
            ctx.setVariable("orderId", order.getId());
            ctx.setVariable("totalPrice", order.getTotalPrice());

            String htmlBody = templateEngine.process("emails/order-cancellation", ctx);
            sendHtmlEmail(user.getEmail(),
                    "Order Cancelled #" + order.getId() + " — EcommerceApp",
                    htmlBody);

            log.info("Cancellation email sent to {} for order #{}", user.getEmail(), order.getId());
        } catch (Exception e) {
            log.error("Failed to send cancellation email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ── Welcome Email ─────────────────────────────────────────────────────────

    @Async
    public void sendWelcome(User user) {
        try {
            Context ctx = new Context();
            ctx.setVariable("firstName", user.getFirstName());
            ctx.setVariable("email", user.getEmail());

            String htmlBody = templateEngine.process("emails/welcome", ctx);
            sendHtmlEmail(user.getEmail(), "Welcome to EcommerceApp!", htmlBody);

            log.info("Welcome email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ── Low stock alert to admin ──────────────────────────────────────────────

    @Async
    public void sendLowStockAlert(String productName, int remainingStock) {
        try {
            String html = "<h2>Low Stock Alert</h2>"
                    + "<p>Product <strong>" + productName + "</strong> has only "
                    + "<strong>" + remainingStock + "</strong> units remaining.</p>"
                    + "<p>Please restock soon.</p>";

            sendHtmlEmail(fromAddress, "Low Stock Alert: " + productName, html);
            log.info("Low stock alert sent for {}", productName);
        } catch (Exception e) {
            log.error("Failed to send low stock alert: {}", e.getMessage());
        }
    }

    // ── Core send method ──────────────────────────────────────────────────────

    private void sendHtmlEmail(String to, String subject, String htmlBody)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        // MimeMessageHelper: easier API for building MIME messages
        // true = multipart (needed for HTML + plain text fallback)
        // "UTF-8" = charset for proper emoji/international character support
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        try {
            helper.setFrom(fromAddress, fromName);
        } catch (java.io.UnsupportedEncodingException e) {
            helper.setFrom(fromAddress);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = isHtml
        mailSender.send(message);
    }
}

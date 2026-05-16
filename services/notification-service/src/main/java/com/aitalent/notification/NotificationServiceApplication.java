package com.aitalent.notification;

import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.lang.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final JavaMailSender mailSender;
    @NonNull
    private final String from;

    NotificationController(JavaMailSender mailSender, @Value("${app.from}") @NonNull String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @PostMapping("/email")
    public ResponseEntity<Map<String, Object>> sendEmail(@Valid @RequestBody EmailRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", req.tenantId());
        result.put("to", req.to());
        result.put("subject", req.subject());
        result.put("timestamp", Instant.now().toString());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setFrom(from);
            helper.setTo(req.to());
            helper.setSubject(req.subject());
            helper.setText(req.body());
            mailSender.send(message);

            result.put("status", "SENT");
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            // For local development, the API remains usable even without SMTP credentials.
            log.warn("SMTP unavailable, simulating email send. to={}, subject={}, reason={}", req.to(), req.subject(), ex.getMessage());
            result.put("status", "SIMULATED");
            result.put("reason", "SMTP not configured or unavailable");
            return ResponseEntity.ok(result);
        }
    }
}

record EmailRequest(
        @NotBlank @NonNull String tenantId,
        @Email @NonNull String to,
        @NotBlank @NonNull String subject,
        @NotBlank @NonNull String body
) {}

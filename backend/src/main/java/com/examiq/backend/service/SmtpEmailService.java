package com.examiq.backend.service;

import com.examiq.backend.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real SMTP-backed OTP delivery. Disabled (app.mail.enabled=false) by
 * default so local development never needs real SMTP credentials - in that
 * mode the OTP is printed to the backend console instead, clearly labeled
 * as a development fallback. When enabled, a delivery failure is never
 * swallowed: it is logged (without the OTP or any SMTP credentials) and
 * surfaced to the caller as an EmailDeliveryException, so the API never
 * reports "code sent" for a code that was not actually sent.
 */
@Service
@Profile("!test")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@examiq.local}")
    private String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendOtpEmail(String toEmail, String otpCode) {
        if (!mailEnabled) {
            System.out.println("[DEV MODE - MAIL_ENABLED not set] Admin login OTP for " + toEmail + ": " + otpCode);
            return;
        }
        log.info("OTP email sending started for {}", toEmail);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Your ExamIQ admin login verification code");
            message.setText("Your verification code is: " + otpCode
                    + "\n\nThis code expires in 5 minutes. If you did not attempt to log in, "
                    + "please secure your account.");
            mailSender.send(message);
            log.info("OTP email sent successfully to {}", toEmail);
        } catch (MailException e) {
            // Logs the exception type/message only (JavaMail's own error text,
            // e.g. "535 Username and Password not accepted") - never the OTP
            // code and never the SMTP username/password.
            log.error("OTP email sending failed for {}: {}: {}", toEmail, e.getClass().getSimpleName(), e.getMessage());
            throw new EmailDeliveryException(
                    "Failed to send the verification email. Please try again shortly or contact an administrator.",
                    e);
        }
    }
}

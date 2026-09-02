package com.examiq.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real SMTP-backed OTP delivery. Disabled (app.mail.enabled=false) by
 * default so local development never needs real SMTP credentials - in that
 * mode the OTP is printed to the backend console instead, clearly labeled
 * as a development fallback, never silently swallowed.
 */
@Service
@Profile("!test")
public class SmtpEmailService implements EmailService {

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
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Your ExamIQ admin login verification code");
            message.setText("Your verification code is: " + otpCode
                    + "\n\nThis code expires in 5 minutes. If you did not attempt to log in, "
                    + "please secure your account.");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send OTP email to " + toEmail + ": " + e.getMessage());
            System.out.println("[FALLBACK - email send failed] Admin login OTP for " + toEmail + ": " + otpCode);
        }
    }
}

package com.examiq.backend.service;

/**
 * Abstracted so the "test" profile can swap in a capturing test double
 * (backend/src/test/.../support/TestEmailService.java) instead of a real
 * SMTP client - no test needs a live mail server.
 */
public interface EmailService {
    void sendOtpEmail(String toEmail, String otpCode);
}

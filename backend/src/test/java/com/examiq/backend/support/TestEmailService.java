package com.examiq.backend.support;

import com.examiq.backend.service.EmailService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only stand-in for EmailService (active only under the "test" Spring
 * profile - see application-test.yml / @ActiveProfiles("test") on the
 * integration tests). Captures the last OTP sent to each address instead of
 * hitting a real SMTP server, so tests can assert on the actual code a
 * "student" would have received by email.
 */
@Service
@Profile("test")
public class TestEmailService implements EmailService {

    private final Map<String, String> lastOtpByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendOtpEmail(String toEmail, String otpCode) {
        lastOtpByEmail.put(toEmail, otpCode);
    }

    public String getLastOtp(String toEmail) {
        return lastOtpByEmail.get(toEmail);
    }
}

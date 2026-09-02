package com.examiq.backend.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for admin-login OTPs. In-memory is appropriate here: this
 * is a single-instance application (matches the rest of its architecture -
 * no cache/session store already exists), and OTPs are short-lived
 * (5 minutes) with no need to survive a restart. A multi-instance
 * deployment would need to move this to a shared store (DB/Redis) - noted
 * as a follow-up, not a requirement of the current architecture.
 */
@Service
public class AdminOtpService {

    private static final int EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    private record OtpRecord(String code, LocalDateTime expiresAt, int attemptsRemaining) {
    }

    private final Map<String, OtpRecord> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String generateAndStore(String username) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        store.put(key(username), new OtpRecord(code, LocalDateTime.now().plusMinutes(EXPIRY_MINUTES), MAX_ATTEMPTS));
        return code;
    }

    /** Verifies and, on success, consumes the code (single-use). */
    public boolean verify(String username, String submittedCode) {
        String k = key(username);
        OtpRecord record = store.get(k);
        if (record == null || submittedCode == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(record.expiresAt())) {
            store.remove(k);
            return false;
        }
        if (record.code().equals(submittedCode.trim())) {
            store.remove(k);
            return true;
        }
        int remaining = record.attemptsRemaining() - 1;
        if (remaining <= 0) {
            store.remove(k); // too many wrong attempts - force a fresh login
        } else {
            store.put(k, new OtpRecord(record.code(), record.expiresAt(), remaining));
        }
        return false;
    }

    private String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }
}

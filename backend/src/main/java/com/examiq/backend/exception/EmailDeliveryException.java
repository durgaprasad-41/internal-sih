package com.examiq.backend.exception;

/**
 * Thrown when an OTP (or other transactional) email could not actually be
 * delivered - e.g. SMTP authentication or connection failure. Never carries
 * the underlying SMTP credentials or the OTP code itself, only a message
 * safe to return to the client and log.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.examiq.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String role;
    private String fullName;

    // True when this account requires email OTP verification before a token
    // is issued (currently ADMIN only) - token/role/fullName are null in
    // that case until /api/auth/verify-otp succeeds.
    private boolean otpRequired;
    private String message;
}

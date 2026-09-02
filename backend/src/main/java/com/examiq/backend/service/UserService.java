package com.examiq.backend.service;

import com.examiq.backend.dto.AuthRequest;
import com.examiq.backend.dto.AuthResponse;
import com.examiq.backend.dto.RegisterRequest;
import com.examiq.backend.dto.VerifyOtpRequest;
import com.examiq.backend.entity.Role;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.RoleRepository;
import com.examiq.backend.repository.UserRepository;
import com.examiq.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdminOtpService adminOtpService;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AdminOtpService adminOtpService,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.adminOtpService = adminOtpService;
        this.emailService = emailService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Prevent ADMIN role registration through public endpoint
        String requestedRole = request.getRole().toUpperCase();
        if ("ADMIN".equals(requestedRole)) {
            throw new IllegalArgumentException("Admin registration is not allowed through public registration. Contact system administrator.");
        }

        Role role = roleRepository.findByName(requestedRole)
                .orElseThrow(() -> new IllegalArgumentException("Invalid role"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(role);
        user.setStatus("ACTIVE");
        userRepository.save(user);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername(request.getUsername());
        authRequest.setPassword(request.getPassword());
        return login(authRequest);
    }

    /**
     * Step 1 of admin login: verifies the password as before. For ADMIN
     * accounts this does NOT issue a token - it generates and emails a
     * one-time code and returns otpRequired=true instead; the token is only
     * issued after verifyOtp() succeeds. STUDENT/FACULTY are unaffected and
     * still get a token immediately, exactly as before.
     */
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (isAdmin(user)) {
            String otp = adminOtpService.generateAndStore(user.getUsername());
            emailService.sendOtpEmail(user.getEmail(), otp);

            AuthResponse response = new AuthResponse();
            response.setOtpRequired(true);
            response.setUsername(user.getUsername());
            response.setMessage("A verification code has been sent to your registered email.");
            return response;
        }

        return issueToken(user);
    }

    /** Step 2 of admin login: verifies the emailed code and, only then, issues the JWT. */
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!isAdmin(user)) {
            throw new IllegalArgumentException("Verification code is not applicable for this account");
        }
        if (!adminOtpService.verify(user.getUsername(), request.getCode())) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }

        return issueToken(user);
    }

    public AuthResponse createAdmin(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalArgumentException("Admin role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(adminRole);
        user.setStatus("ACTIVE");
        userRepository.save(user);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername(request.getUsername());
        authRequest.setPassword(request.getPassword());
        return login(authRequest);
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getName());
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        user.getUsername(),
                        user.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()))));

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setRole(user.getRole().getName());
        response.setFullName(user.getFullName());
        response.setOtpRequired(false);
        return response;
    }
}

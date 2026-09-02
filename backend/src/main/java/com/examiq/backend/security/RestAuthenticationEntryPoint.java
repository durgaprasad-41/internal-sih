package com.examiq.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityResponseWriter responseWriter;

    public RestAuthenticationEntryPoint(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        responseWriter.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required to access this resource");
    }
}

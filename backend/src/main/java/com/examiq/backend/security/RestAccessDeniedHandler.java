package com.examiq.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter responseWriter;

    public RestAccessDeniedHandler(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        responseWriter.writeError(response, HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this resource");
    }
}

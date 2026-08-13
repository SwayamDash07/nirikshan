package com.nirikshan.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    private final JwtService jwt;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public JwtFilter(JwtService jwt, UserRepository users, ObjectMapper mapper) {
        this.jwt = jwt;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String email = jwt.email(header.substring(7));
            var account = users.findByEmailIgnoreCase(email).filter(user -> user.isActive());
            if (account.isEmpty()) {
                log.warn("Bearer token rejected because the account is missing or inactive");
                unauthorized(response, "Account is inactive or no longer exists");
                return;
            }
            var user = account.get();
            String uri = request.getRequestURI();
            if (user.isMustChangePassword() && !uri.equals("/api/auth/me") && !uri.equals("/api/auth/change-password")) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "You must change your temporary password before continuing");
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Bearer token rejected for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
            unauthorized(response, "Invalid or expired authentication token");
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), Map.of("error", message, "message", message, "status", status));
    }
}

package com.nirikshan.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter, ObjectMapper mapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, mapper, 401, "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, mapper, 403, "You do not have permission to access this resource")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/health", "/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/assistant/chat").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/risk-events").permitAll()
                        .requestMatchers("/api/admin/**", "/api/jobs/**", "/api/risk-events/**", "/api/zones/**", "/job-files/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/alerts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/customer").authenticated()
                        .requestMatchers("/api/recommendations/**").hasRole("ADMIN")
                        .requestMatchers("/api/security/**").hasRole("SECURITY")
                        .requestMatchers(HttpMethod.GET, "/api/citizen-reports/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/citizen-reports/**").hasAnyRole("CITIZEN", "ADMIN")
                        .requestMatchers("/api/venues/**", "/api/alerts/**").authenticated()
                        .anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), Map.of("error", message, "message", message, "status", status));
    }
}

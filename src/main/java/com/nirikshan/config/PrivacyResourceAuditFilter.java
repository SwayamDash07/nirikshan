package com.nirikshan.config;

import com.nirikshan.service.PrivacyAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrivacyResourceAuditFilter extends OncePerRequestFilter {
    private static final Pattern JOB_FILE = Pattern.compile("/job-files/(\\d+)/.*");
    private final PrivacyAuditService audit;
    public PrivacyResourceAuditFilter(PrivacyAuditService audit) { this.audit = audit; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        Matcher match = JOB_FILE.matcher(request.getRequestURI());
        if (match.matches() && response.getStatus() < 400) audit.record("ACCESS", "PROCESSED_FOOTAGE", Long.valueOf(match.group(1)), "Sanitized job artifact accessed");
    }
}

package com.tavares.recommender.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /api/**}: caps the request body size, enforces the optional access token and applies
 * rate limiting. Runs before any controller (and therefore before any paid AI call).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiGuardFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Access-Token";
    static final long MAX_BODY_BYTES = 4 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ApiGuardFilter.class);

    private final AppProperties properties;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public ApiGuardFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rateLimiter = new RateLimiter(
            properties.rateLimit().perClientPerMinute(), properties.rateLimit().globalPerMinute());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request is too large.");
            return;
        }

        if (properties.accessTokenEnabled() && !tokenMatches(request.getHeader(TOKEN_HEADER))) {
            // Same treatment for missing and wrong tokens; also throttle guessing.
            if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
                reject(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
                return;
            }
            reject(response, HttpStatus.UNAUTHORIZED, "A valid access token is required.");
            return;
        }

        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            log.warn("Rate limit exceeded for {}", request.getRemoteAddr());
            response.setHeader("Retry-After", "60");
            reject(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean tokenMatches(String supplied) {
        if (supplied == null) {
            return false;
        }
        // Constant-time comparison to avoid leaking the token through timing differences.
        return MessageDigest.isEqual(
            supplied.getBytes(StandardCharsets.UTF_8),
            properties.accessToken().getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

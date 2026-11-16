package com.tavares.recommender.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.tavares.recommender.user.UserDirectory;
import java.util.Optional;
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
    /** Request attribute holding the signed-in user's name; controllers read it, clients can never set it. */
    public static final String USER_ATTRIBUTE = "moodtunes.user";
    static final long MAX_BODY_BYTES = 4 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ApiGuardFilter.class);

    private final UserDirectory directory;
    private final RateLimiter aiLimiter;
    private final RateLimiter generalLimiter;
    private final RateLimiter authFailureLimiter;
    private final ObjectMapper objectMapper;

    public ApiGuardFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.directory = new UserDirectory(properties);
        this.objectMapper = objectMapper;
        var limits = properties.rateLimit();
        this.aiLimiter = new RateLimiter(limits.perClientPerMinute(), limits.globalPerMinute());
        this.generalLimiter = new RateLimiter(limits.generalPerClientPerMinute(), limits.generalGlobalPerMinute());
        this.authFailureLimiter = new RateLimiter(limits.authFailuresPerMinute(), Integer.MAX_VALUE);
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

        Optional<String> user = directory.authenticate(request.getHeader(TOKEN_HEADER));
        if (user.isEmpty()) {
            // Same treatment for missing and wrong codes; also throttle guessing.
            if (!authFailureLimiter.tryAcquire(request.getRemoteAddr())) {
                reject(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
                return;
            }
            reject(response, HttpStatus.UNAUTHORIZED, "A valid access token is required.");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user.get());

        // AI calls cost money, so they get the tight budget; saving and reading mood data is cheap.
        RateLimiter limiter = isAiCall(request) ? aiLimiter : generalLimiter;
        if (!limiter.tryAcquire(request.getRemoteAddr())) {
            log.warn("Rate limit exceeded for {}", request.getRemoteAddr());
            response.setHeader("Retry-After", "60");
            reject(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isAiCall(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/analyze") || path.equals("/api/recommend");
    }

    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

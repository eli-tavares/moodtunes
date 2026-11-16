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

    /** Request header that carries the person's access code. */
    public static final String TOKEN_HEADER = "X-Access-Token";
    /** Request attribute holding the signed-in user's name; controllers read it, clients can never set it. */
    public static final String USER_ATTRIBUTE = "moodtunes.user";
    /** Largest request body accepted, in bytes. Every real request is far smaller (a sentence of at most 500 characters). */
    static final long MAX_BODY_BYTES = 4 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ApiGuardFilter.class);

    private final UserDirectory directory;
    private final RateLimiter aiLimiter;
    private final RateLimiter generalLimiter;
    private final RateLimiter authFailureLimiter;
    private final ObjectMapper objectMapper;

    /**
     * Creates the filter and its three rate limiters from configuration.
     *
     * @param properties  access codes and rate-limit settings ({@code app.*})
     * @param objectMapper used to write the JSON error bodies
     */
    public ApiGuardFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.directory = new UserDirectory(properties);
        this.objectMapper = objectMapper;
        var limits = properties.rateLimit();
        this.aiLimiter = new RateLimiter(limits.perClientPerMinute(), limits.globalPerMinute());
        this.generalLimiter = new RateLimiter(limits.generalPerClientPerMinute(), limits.generalGlobalPerMinute());
        this.authFailureLimiter = new RateLimiter(limits.authFailuresPerMinute(), Integer.MAX_VALUE);
    }

    /**
     * Limits the filter to the API; the static page, the service worker and the health check stay open so the lock
     * screen can load.
     *
     * @param request the incoming request
     * @return {@code true} for anything outside {@code /api/}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    /**
     * Applies, in order: the body-size cap, the access-code check (with throttling of wrong guesses) and the rate
     * limit, then hands the request on. On success the signed-in name is stored in the {@link #USER_ATTRIBUTE}
     * request attribute. A rejected request gets an RFC 7807 JSON body and never reaches a controller, so it can
     * never cause a paid AI call.
     *
     * @param request  the incoming request
     * @param response the response, used only when the request is rejected
     * @param chain    the rest of the filter chain
     * @throws ServletException if a later filter fails
     * @throws IOException      if the error body cannot be written
     */
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

    /**
     * Tells whether the request will trigger a paid model call.
     *
     * @param request the incoming request
     * @return {@code true} for {@code /api/analyze} and {@code /api/recommend}
     */
    private static boolean isAiCall(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/analyze") || path.equals("/api/recommend");
    }

    /**
     * Writes a short problem-details response and ends the request.
     *
     * @param response the response to write
     * @param status   the HTTP status to send
     * @param detail   a message that is safe to show the person
     * @throws IOException if the body cannot be written
     */
    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

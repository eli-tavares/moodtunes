package com.tavares.recommender.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds browser security headers to every response and makes sure the service worker and web manifest are never
 * served stale (otherwise installed copies of the app could not pick up updates).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebHeadersFilter extends OncePerRequestFilter {

    /**
     * Identifies this deployment: the git commit on Railway, otherwise the moment this process started. The page
     * compares it between responses and offers a reload when the server has been redeployed under it.
     */
    static final String VERSION = resolveVersion();

    private static String resolveVersion() {
        String commit = System.getenv("RAILWAY_GIT_COMMIT_SHA");
        return commit != null && commit.length() >= 7 ? commit.substring(0, 7) : "dev-" + System.currentTimeMillis();
    }

    static final String CSP = String.join("; ",
        "default-src 'none'",
        "script-src 'self'",
        "style-src 'self'",
        "img-src 'self'",
        "connect-src 'self'",
        "manifest-src 'self'",
        "worker-src 'self'",
        "base-uri 'none'",
        "form-action 'none'",
        "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-App-Version", VERSION);
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
        } else if (path.equals("/sw.js") || path.equals("/manifest.webmanifest") || path.equals("/")
            || path.equals("/index.html")) {
            response.setHeader("Cache-Control", "no-cache");
        }
        if (path.equals("/manifest.webmanifest")) {
            // Static resource handling no longer maps this extension, so state the proper media type ourselves.
            response.setContentType("application/manifest+json");
        }

        chain.doFilter(request, response);
    }
}

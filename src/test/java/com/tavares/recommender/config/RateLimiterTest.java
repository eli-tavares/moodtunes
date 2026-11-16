package com.tavares.recommender.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;

    @Test
    void limitsPerClientIndependently() {
        var limiter = new RateLimiter(2, 100, ticker);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void globalBudgetCapsAllClients() {
        var limiter = new RateLimiter(10, 3, ticker);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertTrue(limiter.tryAcquire("c"));
        assertFalse(limiter.tryAcquire("d"));
    }

    @Test
    void windowResetsAfterAMinute() {
        var limiter = new RateLimiter(1, 100, ticker);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(61));
        assertTrue(limiter.tryAcquire("a"));
    }
}

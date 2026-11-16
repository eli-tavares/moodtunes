package com.tavares.recommender.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter with a per-client and a global budget. Every call to the AI backend costs money,
 * so the global budget puts a hard ceiling on spend even if many clients (or spoofed IPs) hit the API.
 */
public class RateLimiter {

    private static final String GLOBAL = "*";

    private final int perClientLimit;
    private final int globalLimit;
    private final LoadingCache<String, AtomicInteger> clientWindows;
    private final LoadingCache<String, AtomicInteger> globalWindow;

    /**
     * Creates a limiter that uses the real clock.
     *
     * @param perClientLimit most requests one client may make per minute
     * @param globalLimit    most requests all clients together may make per minute
     */
    public RateLimiter(int perClientLimit, int globalLimit) {
        this(perClientLimit, globalLimit, Ticker.systemTicker());
    }

    /**
     * Creates a limiter with a controllable clock, for tests.
     *
     * @param perClientLimit most requests one client may make per minute
     * @param globalLimit    most requests all clients together may make per minute
     * @param ticker         the time source that decides when a one-minute window ends
     */
    RateLimiter(int perClientLimit, int globalLimit, Ticker ticker) {
        this.perClientLimit = perClientLimit;
        this.globalLimit = globalLimit;
        this.clientWindows = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .ticker(ticker)
            .build(key -> new AtomicInteger());
        this.globalWindow = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .ticker(ticker)
            .build(key -> new AtomicInteger());
    }

    /**
     * Counts one request against the client's budget and the global budget.
     *
     * @param clientKey identifies the client, normally its IP address
     * @return {@code true} if the request may proceed, {@code false} if either budget is exhausted
     */
    public boolean tryAcquire(String clientKey) {
        if (clientWindows.get(clientKey).incrementAndGet() > perClientLimit) {
            return false;
        }
        return globalWindow.get(GLOBAL).incrementAndGet() <= globalLimit;
    }
}

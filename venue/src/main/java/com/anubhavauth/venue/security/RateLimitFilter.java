package com.anubhavauth.venue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Max attempts per window per IP
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 60_000L;  // 1 minute

    private record Bucket(int count, long windowStart) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit the login endpoint
        return !request.getRequestURI().equals("/api/auth/login")
                || !request.getMethod().equals("POST");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip = getClientIp(request);
        long now = Instant.now().toEpochMilli();

        Bucket current = buckets.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MS) {
                return new Bucket(1, now);
            }
            return new Bucket(existing.count() + 1, existing.windowStart());
        });

        if (current.count() > MAX_ATTEMPTS) {
            long retryAfter = (WINDOW_MS - (now - current.windowStart())) / 1000;
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write(
                    "{\"error\":\"Too many login attempts. Try again in "
                            + retryAfter + " seconds.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package dev.tracekit.spring;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Spring Boot specific HTTP utility methods.
 *
 * <p>This class provides helper methods for extracting metadata from HttpServletRequest,
 * particularly useful for adding contextual information to traces in Spring Boot applications.</p>
 */
public final class HttpUtils {

    private HttpUtils() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("HttpUtils is a utility class and cannot be instantiated");
    }

    /**
     * Extract client IP address from HTTP request.
     *
     * <p>Checks X-Forwarded-For, X-Real-IP headers (for proxied requests)
     * and falls back to remoteAddr.</p>
     *
     * <p>This method is useful for adding client IP to traces for DDoS detection
     * and traffic analysis.</p>
     *
     * @param request the HTTP servlet request
     * @return the client IP address, or null if not found
     *
     * @example
     * <pre>{@code
     * @GetMapping("/api/users")
     * public ResponseEntity<?> getUsers(HttpServletRequest request) {
     *     Span span = tracer.spanBuilder("GET /api/users").startSpan();
     *     try (Scope scope = span.makeCurrent()) {
     *         span.setAttribute("http.method", "GET");
     *         span.setAttribute("http.route", "/api/users");
     *         span.setAttribute("http.client_ip", HttpUtils.extractClientIp(request));
     *
     *         // ... handler logic
     *     } finally {
     *         span.end();
     *     }
     * }
     * }</pre>
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // Check X-Forwarded-For header (for requests behind proxy/load balancer)
        // Format: "client, proxy1, proxy2"
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // Take the first IP (the client)
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                String clientIp = ips[0].trim();
                if (isValidIp(clientIp)) {
                    return clientIp;
                }
            }
        }

        // Check X-Real-IP header (alternative proxy header)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            String clientIp = xRealIp.trim();
            if (isValidIp(clientIp)) {
                return clientIp;
            }
        }

        // Fallback to remoteAddr (direct connection)
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.trim().isEmpty() && isValidIp(remoteAddr)) {
            return remoteAddr;
        }

        return null;
    }

    /**
     * Validate if a string is a valid IP address (IPv4 or IPv6).
     *
     * @param ip the IP address string to validate
     * @return true if the string is a valid IP address, false otherwise
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        // Simple validation for IPv4
        String[] ipv4Parts = ip.split("\\.");
        if (ipv4Parts.length == 4) {
            try {
                for (String part : ipv4Parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException e) {
                // Not a valid IPv4, check if it's IPv6
            }
        }

        // Simple validation for IPv6 (contains colons)
        return ip.contains(":");
    }
}

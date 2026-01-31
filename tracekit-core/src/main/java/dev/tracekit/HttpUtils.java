package dev.tracekit;

import java.util.Map;

/**
 * Utility methods for HTTP request processing.
 *
 * <p>This class provides helper methods for extracting metadata from HTTP requests,
 * particularly useful for adding contextual information to traces.</p>
 *
 * <p>Note: For Spring Boot applications with HttpServletRequest, use the headers map approach
 * or create a wrapper method in your application code.</p>
 */
public final class HttpUtils {

    private HttpUtils() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("HttpUtils is a utility class and cannot be instantiated");
    }

    /**
     * Extract client IP address from headers map.
     *
     * <p>This overload is useful for non-Servlet environments or when working
     * with header maps directly.</p>
     *
     * @param headers the HTTP headers map (case-insensitive keys)
     * @param remoteAddr optional remote address fallback
     * @return the client IP address, or null if not found
     *
     * @example
     * <pre>{@code
     * Map<String, String> headers = new HashMap<>();
     * headers.put("x-forwarded-for", "203.0.113.1, 198.51.100.1");
     * String clientIp = HttpUtils.extractClientIp(headers, "127.0.0.1");
     * // Returns: "203.0.113.1"
     * }</pre>
     */
    public static String extractClientIp(Map<String, String> headers, String remoteAddr) {
        if (headers == null) {
            return remoteAddr;
        }

        // Normalize headers to lowercase for case-insensitive lookup
        Map<String, String> normalizedHeaders = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                normalizedHeaders.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }

        // Check X-Forwarded-For header
        String xForwardedFor = normalizedHeaders.get("x-forwarded-for");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                String clientIp = ips[0].trim();
                if (isValidIp(clientIp)) {
                    return clientIp;
                }
            }
        }

        // Check X-Real-IP header
        String xRealIp = normalizedHeaders.get("x-real-ip");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            String clientIp = xRealIp.trim();
            if (isValidIp(clientIp)) {
                return clientIp;
            }
        }

        // Fallback to remoteAddr
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

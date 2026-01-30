package dev.tracekit.local;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Detects if Tracekit Local UI is running on localhost.
 *
 * <p>This class checks if the Local UI server is available by sending
 * a health check request to the configured port. Results are cached
 * to avoid repeated network calls.</p>
 *
 * <p>Thread-safe implementation with synchronized caching.</p>
 */
public class LocalUIDetector {
    private static final Logger logger = LoggerFactory.getLogger(LocalUIDetector.class);

    private static final int DEFAULT_PORT = 9999;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 500;
    private static final String HEALTH_CHECK_PATH = "/api/health";
    private static final String TRACES_PATH = "/v1/traces";

    private final int port;
    private final OkHttpClient httpClient;

    // Thread-safe caching fields
    private volatile Boolean cachedAvailability;
    private volatile String cachedEndpoint;
    private final Object lock = new Object();

    /**
     * Creates a LocalUIDetector with the default port (9999).
     */
    public LocalUIDetector() {
        this(DEFAULT_PORT);
    }

    /**
     * Creates a LocalUIDetector with a custom port.
     *
     * @param port the port number where Local UI is expected to run
     */
    public LocalUIDetector(int port) {
        this.port = port;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Checks if Local UI is available on the configured port.
     *
     * <p>This method performs a health check request to the Local UI server.
     * The result is cached, so subsequent calls will return the cached value
     * without making additional network requests.</p>
     *
     * @return true if Local UI is running and responds with 200 OK, false otherwise
     */
    public boolean isLocalUIAvailable() {
        // Check cache first (volatile read)
        if (cachedAvailability != null) {
            return cachedAvailability;
        }

        // Synchronized block for thread-safe detection
        synchronized (lock) {
            // Double-check after acquiring lock
            if (cachedAvailability != null) {
                return cachedAvailability;
            }

            // Perform health check
            boolean available = performHealthCheck();

            // Cache the result
            cachedAvailability = available;
            if (available) {
                cachedEndpoint = buildTracesEndpoint();
                logger.info("Local UI detected at http://localhost:{}", port);
            } else {
                cachedEndpoint = null;
                logger.debug("Local UI not available at http://localhost:{}", port);
            }

            return available;
        }
    }

    /**
     * Gets the Local UI traces endpoint URL.
     *
     * <p>This method returns the full URL to the traces endpoint if Local UI
     * is available, or null if it's not available.</p>
     *
     * @return the traces endpoint URL, or null if Local UI is not available
     */
    public String getLocalUIEndpoint() {
        // Trigger detection if not done yet
        isLocalUIAvailable();

        // Return cached endpoint (volatile read)
        return cachedEndpoint;
    }

    /**
     * Performs the actual health check HTTP request.
     *
     * @return true if the health check succeeds (200 OK), false otherwise
     */
    private boolean performHealthCheck() {
        String healthCheckUrl = buildHealthCheckUrl();
        Request request = new Request.Builder()
                .url(healthCheckUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful(); // Returns true for 2xx status codes
        } catch (Exception e) {
            // Connection failed, timeout, or other error
            logger.debug("Health check failed for Local UI at {}: {}",
                    healthCheckUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Builds the health check URL.
     *
     * @return the health check URL
     */
    private String buildHealthCheckUrl() {
        return String.format("http://localhost:%d%s", port, HEALTH_CHECK_PATH);
    }

    /**
     * Builds the traces endpoint URL.
     *
     * @return the traces endpoint URL
     */
    private String buildTracesEndpoint() {
        return String.format("http://localhost:%d%s", port, TRACES_PATH);
    }
}

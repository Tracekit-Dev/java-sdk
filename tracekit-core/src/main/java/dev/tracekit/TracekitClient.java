package dev.tracekit;

import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for sending trace data to Tracekit endpoints.
 * Supports dual-send to both cloud and local endpoints.
 *
 * <p>This client is thread-safe and can be reused across multiple requests.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * TracekitConfig config = TracekitConfig.builder()
 *     .apiKey("your-api-key")
 *     .serviceName("my-service")
 *     .build();
 *
 * TracekitClient client = new TracekitClient(config, "http://localhost:9999/traces");
 *
 * Map&lt;String, Object&gt; traceData = new HashMap&lt;&gt;();
 * traceData.put("traceId", "abc123");
 * traceData.put("spanId", "def456");
 *
 * boolean success = client.sendTrace(traceData);
 * </pre>
 */
public final class TracekitClient {

    private static final Logger logger = LoggerFactory.getLogger(TracekitClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String VERSION = "1.0.0"; // TODO: Load from build metadata
    private static final String USER_AGENT = "tracekit-java-sdk/" + VERSION;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;
    private static final int WRITE_TIMEOUT_SECONDS = 10;

    private final TracekitConfig config;
    private final String localUIEndpoint;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * Creates a new TracekitClient.
     *
     * @param config the Tracekit configuration (must not be null)
     * @param localUIEndpoint optional local UI endpoint URL (may be null or empty)
     * @throws NullPointerException if config is null
     */
    public TracekitClient(TracekitConfig config, String localUIEndpoint) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.localUIEndpoint = normalizeEndpoint(localUIEndpoint);
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        logger.debug("TracekitClient initialized with cloud endpoint: {}, local endpoint: {}",
                config.getEndpoint(), this.localUIEndpoint != null ? this.localUIEndpoint : "none");
    }

    /**
     * Sends trace data to configured endpoints.
     *
     * <p>This method attempts to send trace data to both cloud and local endpoints
     * (if configured). The operation is considered successful if at least one
     * endpoint accepts the data.</p>
     *
     * <p>Cloud endpoint failures are logged as warnings, while local endpoint
     * failures are logged as debug messages (since local endpoint is optional).</p>
     *
     * @param traceData the trace data to send (must not be null)
     * @return true if at least one endpoint succeeded, false if all endpoints failed
     * @throws NullPointerException if traceData is null
     */
    public boolean sendTrace(Map<String, Object> traceData) {
        Objects.requireNonNull(traceData, "traceData must not be null");

        String jsonBody = gson.toJson(traceData);
        boolean cloudSuccess = sendToCloud(jsonBody);
        boolean localSuccess = sendToLocal(jsonBody);

        boolean overallSuccess = cloudSuccess || localSuccess;

        if (overallSuccess) {
            logger.debug("Trace sent successfully (cloud: {}, local: {})", cloudSuccess, localSuccess);
        } else {
            logger.warn("Failed to send trace to all endpoints");
        }

        return overallSuccess;
    }

    /**
     * Sends trace data to the cloud endpoint.
     *
     * @param jsonBody the JSON-serialized trace data
     * @return true if cloud endpoint accepted the data, false otherwise
     */
    private boolean sendToCloud(String jsonBody) {
        String cloudEndpoint = config.getEndpoint();

        try {
            Request request = new Request.Builder()
                    .url(cloudEndpoint)
                    .header("User-Agent", USER_AGENT)
                    .header("X-API-Key", config.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.debug("Successfully sent trace to cloud endpoint: {}", cloudEndpoint);
                    return true;
                } else {
                    logger.warn("Cloud endpoint returned error: {} - {}",
                            response.code(), response.message());
                    return false;
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to send trace to cloud endpoint: {}", cloudEndpoint, e);
            return false;
        }
    }

    /**
     * Sends trace data to the local UI endpoint (if configured).
     *
     * @param jsonBody the JSON-serialized trace data
     * @return true if local endpoint accepted the data (or if no local endpoint configured),
     *         false if local endpoint failed
     */
    private boolean sendToLocal(String jsonBody) {
        if (localUIEndpoint == null) {
            return false; // No local endpoint configured
        }

        try {
            Request request = new Request.Builder()
                    .url(localUIEndpoint)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.debug("Successfully sent trace to local endpoint: {}", localUIEndpoint);
                    return true;
                } else {
                    logger.debug("Local endpoint returned error: {} - {}",
                            response.code(), response.message());
                    return false;
                }
            }
        } catch (IOException e) {
            logger.debug("Failed to send trace to local endpoint: {}", localUIEndpoint, e);
            return false;
        }
    }

    /**
     * Normalizes an endpoint URL by treating null, empty, or whitespace-only strings as null.
     *
     * @param endpoint the endpoint URL to normalize
     * @return the normalized endpoint URL, or null if the input was null/empty/whitespace
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return null;
        }
        return endpoint.trim();
    }

    /**
     * Gets the configured cloud endpoint URL.
     *
     * @return the cloud endpoint URL
     */
    public String getCloudEndpoint() {
        return config.getEndpoint();
    }

    /**
     * Gets the configured local UI endpoint URL.
     *
     * @return the local UI endpoint URL, or null if not configured
     */
    public String getLocalEndpoint() {
        return localUIEndpoint;
    }
}

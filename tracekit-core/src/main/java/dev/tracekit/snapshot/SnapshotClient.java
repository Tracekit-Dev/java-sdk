package dev.tracekit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.tracekit.security.SensitiveDataDetector;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SnapshotClient {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotClient.class);
    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>) (instant, type, context) ->
                instant == null ? null : new com.google.gson.JsonPrimitive(instant.toString()))
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonDeserializer<Instant>) (json, typeOfT, context) ->
                json.isJsonNull() ? null : Instant.parse(json.getAsString()))
            .create();

    private final String apiKey;
    private final String baseURL;
    private final String serviceName;
    private final HttpClient httpClient;
    private final SensitiveDataDetector securityDetector;

    private final Map<String, BreakpointConfig> breakpointsCache = new ConcurrentHashMap<>();
    private final Set<String> registrationCache = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile Instant lastFetch;

    // Kill switch: server-initiated monitoring disable
    private volatile boolean killSwitchActive = false;
    private ScheduledFuture<?> pollFuture;

    // SSE (Server-Sent Events) real-time updates
    private volatile String sseEndpoint = null;
    private volatile boolean sseActive = false;
    private Thread sseThread = null;
    private volatile boolean sseStop = false;

    // Opt-in capture limits (all disabled by default: 0 = unlimited)
    private int captureDepth = 0;
    private int maxPayload = 0;
    private long captureTimeoutMs = 0;

    // Circuit breaker state (synchronized via circuitBreakerLock)
    private final Object circuitBreakerLock = new Object();
    private final List<Long> circuitBreakerFailureTimestamps = new ArrayList<>();
    private String circuitBreakerState = "closed";
    private long circuitBreakerOpenedAt = 0;
    private int circuitBreakerMaxFailures = 3;
    private long circuitBreakerWindowMs = 60000;
    private long circuitBreakerCooldownMs = 300000;
    private final List<Map<String, Object>> pendingTelemetryEvents = new ArrayList<>();

    public SnapshotClient(String apiKey, String baseURL, String serviceName) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
        this.serviceName = serviceName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.securityDetector = new SensitiveDataDetector();
    }

    /**
     * Configure circuit breaker thresholds (0 = use default for that parameter).
     */
    public void configureCircuitBreaker(int maxFailures, long windowMs, long cooldownMs) {
        synchronized (circuitBreakerLock) {
            if (maxFailures > 0) this.circuitBreakerMaxFailures = maxFailures;
            if (windowMs > 0) this.circuitBreakerWindowMs = windowMs;
            if (cooldownMs > 0) this.circuitBreakerCooldownMs = cooldownMs;
        }
    }

    private boolean circuitBreakerShouldAllow() {
        synchronized (circuitBreakerLock) {
            if ("closed".equals(circuitBreakerState)) {
                return true;
            }
            if (System.currentTimeMillis() - circuitBreakerOpenedAt >= circuitBreakerCooldownMs) {
                circuitBreakerState = "closed";
                circuitBreakerFailureTimestamps.clear();
                logger.info("TraceKit: Code monitoring resumed");
                return true;
            }
            return false;
        }
    }

    private boolean circuitBreakerRecordFailure() {
        synchronized (circuitBreakerLock) {
            long now = System.currentTimeMillis();
            circuitBreakerFailureTimestamps.add(now);
            long cutoff = now - circuitBreakerWindowMs;
            circuitBreakerFailureTimestamps.removeIf(ts -> ts <= cutoff);

            if (circuitBreakerFailureTimestamps.size() >= circuitBreakerMaxFailures
                    && "closed".equals(circuitBreakerState)) {
                circuitBreakerState = "open";
                circuitBreakerOpenedAt = now;
                logger.warn("TraceKit: Code monitoring paused ({} capture failures in {}s). Auto-resumes in {} min.",
                        circuitBreakerMaxFailures, circuitBreakerWindowMs / 1000, circuitBreakerCooldownMs / 60000);
                return true;
            }
            return false;
        }
    }

    private void queueCircuitBreakerEvent() {
        synchronized (pendingTelemetryEvents) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "circuit_breaker_tripped");
            event.put("service_name", serviceName);
            event.put("failure_count", circuitBreakerMaxFailures);
            event.put("window_seconds", circuitBreakerWindowMs / 1000);
            event.put("cooldown_seconds", circuitBreakerCooldownMs / 1000);
            event.put("timestamp", Instant.now().toString());
            pendingTelemetryEvents.add(event);
        }
    }

    private List<Map<String, Object>> drainPendingEvents() {
        synchronized (pendingTelemetryEvents) {
            if (pendingTelemetryEvents.isEmpty()) return Collections.emptyList();
            List<Map<String, Object>> drained = new ArrayList<>(pendingTelemetryEvents);
            pendingTelemetryEvents.clear();
            return drained;
        }
    }

    /** Set opt-in capture depth limit. 0 = unlimited (default). */
    public void setCaptureDepth(int depth) { this.captureDepth = depth; }

    /** Set opt-in max payload size in bytes. 0 = unlimited (default). */
    public void setMaxPayload(int bytes) { this.maxPayload = bytes; }

    /** Set opt-in capture timeout in milliseconds. 0 = no timeout (default). */
    public void setCaptureTimeoutMs(long ms) { this.captureTimeoutMs = ms; }

    public int getCaptureDepth() { return captureDepth; }
    public int getMaxPayload() { return maxPayload; }
    public long getCaptureTimeoutMs() { return captureTimeoutMs; }

    public void start() {
        pollFuture = scheduler.scheduleAtFixedRate(
            this::fetchActiveBreakpoints,
            0,
            30,
            TimeUnit.SECONDS
        );
        logger.info("📸 TraceKit Snapshot Client started for service: {}", serviceName);
    }

    private void reschedulePolling(long intervalSeconds) {
        if (pollFuture != null) {
            pollFuture.cancel(false);
        }
        pollFuture = scheduler.scheduleAtFixedRate(
            this::fetchActiveBreakpoints,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        sseStop = true;
        sseActive = false;
        if (sseThread != null) {
            sseThread.interrupt();
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("📸 TraceKit Snapshot Client stopped");
    }

    /**
     * Check and capture with context. Crash isolation: catches all Throwable
     * (including OutOfMemoryError, StackOverflowError) to never crash the host app.
     */
    public void checkAndCaptureWithContext(String label, Map<String, Object> variables) {
        try {
            doCheckAndCapture(label, variables);
        } catch (Throwable t) {
            // Crash isolation: never let TraceKit bugs crash the host application
            logger.error("TraceKit: error in checkAndCaptureWithContext", t);
        }
    }

    private void doCheckAndCapture(String label, Map<String, Object> variables) {
        // Kill switch: skip all capture when server has disabled monitoring
        if (killSwitchActive) {
            return;
        }

        logger.debug("checkAndCaptureWithContext called with label: {}, cache size: {}", label, breakpointsCache.size());

        StackTraceElement caller = getCaller();
        if (caller == null) {
            logger.warn("Could not detect caller location");
            return;
        }

        String filePath = getFullFilePath(caller);
        int lineNumber = caller.getLineNumber();
        String functionName = caller.getClassName() + "." + caller.getMethodName();

        logger.debug("Caller detected: file={}, line={}, function={}", filePath, lineNumber, functionName);

        autoRegisterBreakpoint(filePath, lineNumber, functionName, label);

        String locationKey = functionName + ":" + label;
        logger.debug("Looking up breakpoint with label key: {}", locationKey);
        BreakpointConfig breakpoint = breakpointsCache.get(locationKey);

        if (breakpoint == null) {
            String lineKey = filePath + ":" + lineNumber;
            logger.debug("Label key not found, trying line key: {}", lineKey);
            breakpoint = breakpointsCache.get(lineKey);
            logger.debug("Breakpoint not found by label key {}, trying line key {}: {}", locationKey, lineKey, breakpoint != null ? "found" : "not found");
        } else {
            logger.debug("Breakpoint found by label key: {} (enabled={})", breakpoint.id, breakpoint.enabled);
        }

        if (breakpoint == null) {
            logger.debug("No breakpoint found in cache for {} or {}, skipping capture (cache size: {})", locationKey, filePath + ":" + lineNumber, breakpointsCache.size());
            logger.debug("Cache keys: {}", breakpointsCache.keySet());
            return;
        }

        if (!breakpoint.enabled) {
            logger.debug("Breakpoint {} exists but is disabled, skipping capture", breakpoint.id);
            return;
        }

        if (breakpoint.expireAt != null && Instant.now().isAfter(breakpoint.expireAt)) {
            logger.debug("Breakpoint {} expired at {}, skipping capture", breakpoint.id, breakpoint.expireAt);
            return;
        }

        if (breakpoint.maxCaptures > 0 && breakpoint.captureCount >= breakpoint.maxCaptures) {
            logger.debug("Breakpoint {} reached max captures ({}/{}), skipping", breakpoint.id, breakpoint.captureCount, breakpoint.maxCaptures);
            return;
        }

        // Apply opt-in capture depth limit
        if (captureDepth > 0) {
            variables = limitDepth(variables, 0);
        }

        logger.debug("All checks passed, preparing to capture snapshot for breakpoint: {}", breakpoint.id);

        String stackTrace = Arrays.stream(Thread.currentThread().getStackTrace())
                .skip(2)
                .map(ste -> ste.toString())
                .collect(Collectors.joining("\n"));

        SpanContext spanContext = Span.current().getSpanContext();
        String traceId = spanContext.isValid() && spanContext.isSampled() ? spanContext.getTraceId() : null;
        String spanId = spanContext.isValid() && spanContext.isSampled() ? spanContext.getSpanId() : null;

        logger.debug("Scanning {} variables for security issues", variables.size());
        SecurityScanResult scanResult = scanForSecurityIssues(variables);
        logger.debug("Security scan complete: {} flags found", scanResult.securityFlags.size());

        Snapshot snapshot = new Snapshot(
            breakpoint.id,
            serviceName,
            filePath,
            functionName,
            label,
            lineNumber,
            scanResult.sanitizedVariables,
            scanResult.securityFlags,
            stackTrace,
            traceId,
            spanId,
            null,
            Instant.now()
        );

        logger.debug("Submitting snapshot capture asynchronously for breakpoint: {}", breakpoint.id);

        // Apply opt-in capture timeout
        if (captureTimeoutMs > 0) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    captureSnapshot(snapshot);
                } catch (Throwable t) {
                    logger.error("TraceKit: error in async snapshot capture", t);
                }
            });
            try {
                future.get(captureTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.warn("TraceKit: capture timeout exceeded ({}ms), skipping", captureTimeoutMs);
                future.cancel(true);
            } catch (Throwable t) {
                logger.error("TraceKit: error waiting for capture", t);
            }
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    captureSnapshot(snapshot);
                } catch (Throwable t) {
                    logger.error("TraceKit: error in async snapshot capture", t);
                }
            });
        }
    }

    /** Limit variable depth for opt-in capture depth limiting */
    private Map<String, Object> limitDepth(Map<String, Object> data, int currentDepth) {
        if (currentDepth >= captureDepth) {
            Map<String, Object> truncated = new HashMap<>();
            truncated.put("_truncated", true);
            truncated.put("_depth", currentDepth);
            return truncated;
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                result.put(entry.getKey(), limitDepth(mapValue, currentDepth + 1));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private StackTraceElement getCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Stack: [0] getStackTrace, [1] getCaller, [2] checkAndCaptureWithContext,
        //        [3] TracekitSDK.captureSnapshot, [4] actual caller
        if (stackTrace.length > 4) {
            return stackTrace[4];
        }
        return null;
    }

    private String getFullFilePath(StackTraceElement caller) {
        String className = caller.getClassName();
        String fileName = caller.getFileName();

        if (fileName == null) {
            // Fallback: use class name if filename is not available
            return className.replace('.', '/') + ".java";
        }

        // Convert package name to path: com.example.MyClass -> com/example/MyClass.java
        // Extract package path from class name
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            String packagePath = className.substring(0, lastDot).replace('.', '/');
            return packagePath + "/" + fileName;
        }

        // No package, just return filename
        return fileName;
    }

    private void fetchActiveBreakpoints() {
        try {
            String url = baseURL + "/sdk/snapshots/active/" + serviceName;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-API-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("⚠️  Failed to fetch breakpoints: HTTP {}", response.statusCode());
                return;
            }

            logger.debug("Fetch breakpoints response: {}", response.body());
            try {
                BreakpointsResponse result = gson.fromJson(response.body(), BreakpointsResponse.class);
                logger.debug("Parsed {} breakpoints from response", result.breakpoints != null ? result.breakpoints.size() : 0);

                // Handle kill switch state (missing field = false for backward compat)
                boolean newKillState = result.killSwitch != null && result.killSwitch;
                if (newKillState && !killSwitchActive) {
                    logger.warn("TraceKit: Code monitoring disabled by server kill switch. Polling at reduced frequency.");
                    reschedulePolling(60);
                } else if (!newKillState && killSwitchActive) {
                    logger.info("TraceKit: Code monitoring re-enabled by server.");
                    reschedulePolling(30);
                }
                killSwitchActive = newKillState;

                // If kill-switched, close any active SSE connection
                if (killSwitchActive && sseActive) {
                    sseStop = true;
                    sseActive = false;
                    if (sseThread != null) sseThread.interrupt();
                    logger.info("TraceKit: SSE connection closed due to kill switch");
                }

                // SSE auto-discovery: if sse_endpoint present and not already connected
                if (result.sseEndpoint != null && !result.sseEndpoint.isEmpty()
                        && !sseActive && !killSwitchActive
                        && result.breakpoints != null && !result.breakpoints.isEmpty()) {
                    sseEndpoint = result.sseEndpoint;
                    sseStop = false;
                    sseThread = new Thread(() -> connectSSE(result.sseEndpoint));
                    sseThread.setDaemon(true);
                    sseThread.setName("tracekit-sse");
                    sseThread.start();
                }

                updateBreakpointCache(result.breakpoints);
                lastFetch = Instant.now();
            } catch (Exception e) {
                logger.error("⚠️  Failed to parse breakpoints response", e);
            }

        } catch (IOException | InterruptedException e) {
            logger.error("⚠️  Failed to fetch breakpoints", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Connect to SSE endpoint for real-time breakpoint updates.
     * Falls back to polling if SSE connection fails or is interrupted.
     */
    private void connectSSE(String endpoint) {
        try {
            String fullURL = baseURL + endpoint;
            URL url = new URL(fullURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(0); // No read timeout for SSE

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.warn("TraceKit: SSE endpoint returned {}, falling back to polling", responseCode);
                sseActive = false;
                return;
            }

            sseActive = true;
            logger.info("TraceKit: SSE connection established for real-time breakpoint updates");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String eventType = "";
                StringBuilder dataBuffer = new StringBuilder();
                String line;

                while (!sseStop && (line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventType = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (dataBuffer.length() > 0) dataBuffer.append("\n");
                        dataBuffer.append(line.substring(5).trim());
                    } else if (line.isEmpty()) {
                        // Empty line = event boundary
                        if (!eventType.isEmpty() && dataBuffer.length() > 0) {
                            handleSSEEvent(eventType, dataBuffer.toString());
                        }
                        eventType = "";
                        dataBuffer.setLength(0);
                    }
                }
            }

            logger.info("TraceKit: SSE connection closed, falling back to polling");

        } catch (Throwable t) {
            // Crash isolation: catch all including OOM, StackOverflow
            if (!sseStop) {
                logger.warn("TraceKit: SSE connection lost, falling back to polling: {}", t.getMessage());
            }
        } finally {
            sseActive = false;
        }
    }

    /**
     * Process a single SSE event.
     */
    @SuppressWarnings("unchecked")
    private void handleSSEEvent(String eventType, String data) {
        try {
            switch (eventType) {
                case "init": {
                    Map<String, Object> initData = gson.fromJson(data, Map.class);
                    List<Map<String, Object>> bpList = (List<Map<String, Object>>) initData.get("breakpoints");
                    List<BreakpointConfig> breakpoints = new ArrayList<>();
                    if (bpList != null) {
                        // Re-parse via JSON to get proper BreakpointConfig objects
                        String bpJson = gson.toJson(bpList);
                        BreakpointConfig[] bpArray = gson.fromJson(bpJson, BreakpointConfig[].class);
                        breakpoints = Arrays.asList(bpArray);
                    }
                    updateBreakpointCache(breakpoints);
                    Object ks = initData.get("kill_switch");
                    killSwitchActive = ks instanceof Boolean && (Boolean) ks;
                    if (killSwitchActive) {
                        sseStop = true;
                    }
                    logger.info("TraceKit: SSE init received, {} breakpoints loaded", breakpoints.size());
                    break;
                }

                case "breakpoint_created":
                case "breakpoint_updated": {
                    BreakpointConfig bp = gson.fromJson(data, BreakpointConfig.class);
                    if (bp.label != null && !bp.label.isEmpty() && bp.functionName != null) {
                        String labelKey = bp.functionName + ":" + bp.label;
                        breakpointsCache.put(labelKey, bp);
                    }
                    String lineKey = bp.filePath + ":" + bp.lineNumber;
                    breakpointsCache.put(lineKey, bp);
                    logger.info("TraceKit: SSE breakpoint {}: {}", eventType, bp.id);
                    break;
                }

                case "breakpoint_deleted": {
                    Map<String, String> deleteData = gson.fromJson(data, Map.class);
                    String bpId = deleteData.get("id");
                    breakpointsCache.entrySet().removeIf(entry -> entry.getValue().id.equals(bpId));
                    logger.info("TraceKit: SSE breakpoint deleted: {}", bpId);
                    break;
                }

                case "kill_switch": {
                    Map<String, Object> ksData = gson.fromJson(data, Map.class);
                    Object enabled = ksData.get("enabled");
                    killSwitchActive = enabled instanceof Boolean && (Boolean) enabled;
                    reschedulePolling(killSwitchActive ? 60 : 30);
                    if (killSwitchActive) {
                        logger.info("TraceKit: Kill switch enabled via SSE, closing connection");
                        sseStop = true;
                    }
                    break;
                }

                case "heartbeat":
                case "sdk_count":
                    // No action needed -- heartbeat keeps connection alive, sdk_count is for dashboard UI
                    break;

                default:
                    logger.warn("TraceKit: unknown SSE event type: {}", eventType);
            }
        } catch (Throwable t) {
            logger.error("TraceKit: error handling SSE event {}", eventType, t);
        }
    }

    private void updateBreakpointCache(List<BreakpointConfig> breakpoints) {
        Map<String, BreakpointConfig> newCache = new ConcurrentHashMap<>();

        for (BreakpointConfig bp : breakpoints) {
            logger.debug("Processing breakpoint: id={}, file={}, line={}, label={}, function={}, enabled={}",
                bp.id, bp.filePath, bp.lineNumber, bp.label, bp.functionName, bp.enabled);

            if (bp.label != null && !bp.label.isEmpty() && bp.functionName != null) {
                String labelKey = bp.functionName + ":" + bp.label;
                newCache.put(labelKey, bp);
                logger.debug("Added to cache with label key: {}", labelKey);
            }

            String lineKey = bp.filePath + ":" + bp.lineNumber;
            newCache.put(lineKey, bp);
            logger.debug("Added to cache with line key: {}", lineKey);
        }

        breakpointsCache.clear();
        breakpointsCache.putAll(newCache);

        logger.info("📸 Updated breakpoint cache: {} active breakpoints, cache size: {}", breakpoints.size(), breakpointsCache.size());
    }

    private void autoRegisterBreakpoint(String filePath, int lineNumber, String functionName, String label) {
        String regKey = functionName + ":" + label;

        if (registrationCache.contains(regKey)) {
            logger.debug("Breakpoint already registered: {}", regKey);
            return;
        }

        registrationCache.add(regKey);
        logger.info("Auto-registering breakpoint: {} at {}:{}", label, filePath, lineNumber);

        CompletableFuture.runAsync(() -> {
            try {
                String url = baseURL + "/sdk/snapshots/auto-register";

                Map<String, Object> payload = new HashMap<>();
                payload.put("service_name", serviceName);
                payload.put("file_path", filePath);
                payload.put("line_number", lineNumber);
                payload.put("function_name", functionName);
                payload.put("label", label);

                String jsonPayload = gson.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("X-API-Key", apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                logger.info("Auto-register response: HTTP {}", response.statusCode());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    logger.info("Breakpoint registered successfully, refreshing cache");
                    Thread.sleep(500);
                    fetchActiveBreakpoints();
                } else {
                    logger.warn("Failed to register breakpoint: HTTP {} - {}", response.statusCode(), response.body());
                }

            } catch (IOException | InterruptedException e) {
                logger.error("⚠️  Failed to auto-register breakpoint", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void captureSnapshot(Snapshot snapshot) {
        // Circuit breaker check
        if (!circuitBreakerShouldAllow()) {
            return;
        }

        try {
            String url = baseURL + "/sdk/snapshots/capture";
            String jsonPayload;
            try {
                jsonPayload = gson.toJson(snapshot);
            } catch (Throwable t) {
                // Serialization error -- do NOT count as circuit breaker failure
                logger.error("TraceKit: serialization error, sending minimal snapshot", t);
                Map<String, Object> fallbackVars = new HashMap<>();
                fallbackVars.put("_error", "serialization failed: " + t.getMessage());
                snapshot = new Snapshot(snapshot.breakpointId, snapshot.serviceName, snapshot.filePath,
                    snapshot.functionName, snapshot.label, snapshot.lineNumber, fallbackVars,
                    snapshot.securityFlags, snapshot.stackTrace, snapshot.traceId, snapshot.spanId,
                    snapshot.requestContext, snapshot.capturedAt);
                jsonPayload = gson.toJson(snapshot);
            }

            // Apply opt-in max payload limit
            if (maxPayload > 0 && jsonPayload.getBytes().length > maxPayload) {
                Map<String, Object> truncatedVars = new HashMap<>();
                truncatedVars.put("_truncated", true);
                truncatedVars.put("_payload_size", jsonPayload.getBytes().length);
                truncatedVars.put("_max_payload", maxPayload);
                snapshot = new Snapshot(snapshot.breakpointId, snapshot.serviceName, snapshot.filePath,
                    snapshot.functionName, snapshot.label, snapshot.lineNumber, truncatedVars,
                    snapshot.securityFlags, snapshot.stackTrace, snapshot.traceId, snapshot.spanId,
                    snapshot.requestContext, snapshot.capturedAt);
                jsonPayload = gson.toJson(snapshot);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-API-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                logger.info("Snapshot captured: {}", snapshot.label != null ? snapshot.label : snapshot.filePath);
            } else if (response.statusCode() >= 500) {
                // Server error -- count as circuit breaker failure
                logger.error("Failed to capture snapshot: HTTP {}", response.statusCode());
                if (circuitBreakerRecordFailure()) {
                    queueCircuitBreakerEvent();
                }
            } else {
                // Client error (4xx) -- do NOT count as circuit breaker failure
                logger.error("Failed to capture snapshot: HTTP {}", response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            // Network/timeout error -- count as circuit breaker failure
            logger.error("TraceKit: error in captureSnapshot", e);
            if (circuitBreakerRecordFailure()) {
                queueCircuitBreakerEvent();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (Throwable t) {
            // Crash isolation: catch all including OOM, StackOverflow
            logger.error("TraceKit: error in captureSnapshot", t);
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private SecurityScanResult scanForSecurityIssues(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return new SecurityScanResult(new HashMap<>(), new ArrayList<>());
        }

        // Delegate to SensitiveDataDetector.scanVariables() for typed [REDACTED:type] markers
        SensitiveDataDetector.ScanResult result = securityDetector.scanVariables(variables);

        // Map SensitiveDataDetector.SecurityFlag -> SnapshotClient.SecurityFlag
        // (drop the 'redacted' boolean -- SnapshotClient's SecurityFlag doesn't have it)
        List<SecurityFlag> mappedFlags = new ArrayList<>();
        for (SensitiveDataDetector.SecurityFlag flag : result.getSecurityFlags()) {
            mappedFlags.add(new SecurityFlag(flag.getType(), flag.getSeverity(), flag.getVariable()));
        }

        return new SecurityScanResult(result.getSanitizedVariables(), mappedFlags);
    }

    static class BreakpointsResponse {
        public List<BreakpointConfig> breakpoints = new ArrayList<>();

        @SerializedName("kill_switch")
        public Boolean killSwitch;

        @SerializedName("sse_endpoint")
        public String sseEndpoint;
    }

    static class BreakpointConfig {
        public String id;

        @SerializedName("service_name")
        public String serviceName;

        @SerializedName("file_path")
        public String filePath;

        @SerializedName("function_name")
        public String functionName;

        public String label;

        @SerializedName("line_number")
        public int lineNumber;

        public String condition;

        @SerializedName("max_captures")
        public int maxCaptures;

        @SerializedName("capture_count")
        public int captureCount;

        @SerializedName("expire_at")
        public Instant expireAt;

        public boolean enabled;

        @SerializedName("capture_frequency")
        public int captureFrequency;

        public Map<String, Object> metadata;
    }

    public static class SecurityFlag {
        public final String type;
        public final String severity;
        public final String variable;

        public SecurityFlag(String type, String severity, String variable) {
            this.type = type;
            this.severity = severity;
            this.variable = variable;
        }

        public String getType() { return type; }
        public String getSeverity() { return severity; }
        public String getVariable() { return variable; }
    }

    static class SecurityScanResult {
        public final Map<String, Object> sanitizedVariables;
        public final List<SecurityFlag> securityFlags;

        public SecurityScanResult(Map<String, Object> sanitizedVariables, List<SecurityFlag> securityFlags) {
            this.sanitizedVariables = sanitizedVariables;
            this.securityFlags = securityFlags;
        }
    }

    static class Snapshot {
        @SerializedName("breakpoint_id")
        public final String breakpointId;

        @SerializedName("service_name")
        public final String serviceName;

        @SerializedName("file_path")
        public final String filePath;

        @SerializedName("function_name")
        public final String functionName;

        public final String label;

        @SerializedName("line_number")
        public final int lineNumber;

        public final Map<String, Object> variables;

        @SerializedName("security_flags")
        public final List<SecurityFlag> securityFlags;

        @SerializedName("stack_trace")
        public final String stackTrace;

        @SerializedName("trace_id")
        public final String traceId;

        @SerializedName("span_id")
        public final String spanId;

        @SerializedName("request_context")
        public final Map<String, Object> requestContext;

        @SerializedName("captured_at")
        public final Instant capturedAt;

        public Snapshot(
            String breakpointId,
            String serviceName,
            String filePath,
            String functionName,
            String label,
            int lineNumber,
            Map<String, Object> variables,
            List<SecurityFlag> securityFlags,
            String stackTrace,
            String traceId,
            String spanId,
            Map<String, Object> requestContext,
            Instant capturedAt
        ) {
            this.breakpointId = breakpointId;
            this.serviceName = serviceName;
            this.filePath = filePath;
            this.functionName = functionName;
            this.label = label;
            this.lineNumber = lineNumber;
            this.variables = variables;
            this.securityFlags = securityFlags;
            this.stackTrace = stackTrace;
            this.traceId = traceId;
            this.spanId = spanId;
            this.requestContext = requestContext;
            this.capturedAt = capturedAt;
        }

        public String getBreakpointId() { return breakpointId; }
        public String getServiceName() { return serviceName; }
        public String getFilePath() { return filePath; }
        public String getFunctionName() { return functionName; }
        public String getLabel() { return label; }
        public int getLineNumber() { return lineNumber; }
        public Map<String, Object> getVariables() { return variables; }
        public List<SecurityFlag> getSecurityFlags() { return securityFlags; }
        public String getStackTrace() { return stackTrace; }
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public Map<String, Object> getRequestContext() { return requestContext; }
        public Instant getCapturedAt() { return capturedAt; }
    }
}

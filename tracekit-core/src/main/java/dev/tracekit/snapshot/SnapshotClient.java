package dev.tracekit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.tracekit.security.SensitiveDataDetector;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
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

    public SnapshotClient(String apiKey, String baseURL, String serviceName) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
        this.serviceName = serviceName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.securityDetector = new SensitiveDataDetector();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
            this::fetchActiveBreakpoints,
            0,
            30,
            TimeUnit.SECONDS
        );
        logger.info("📸 TraceKit Snapshot Client started for service: {}", serviceName);
    }

    public void stop() {
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

    public void checkAndCaptureWithContext(String label, Map<String, Object> variables) {
        logger.debug("🔍 checkAndCaptureWithContext called with label: {}, cache size: {}", label, breakpointsCache.size());

        StackTraceElement caller = getCaller();
        if (caller == null) {
            logger.warn("⚠️  Could not detect caller location");
            return;
        }

        String fileName = caller.getFileName();
        int lineNumber = caller.getLineNumber();
        String functionName = caller.getClassName() + "." + caller.getMethodName();

        logger.debug("🔍 Caller detected: file={}, line={}, function={}", fileName, lineNumber, functionName);

        autoRegisterBreakpoint(fileName, lineNumber, functionName, label);

        String locationKey = functionName + ":" + label;
        logger.debug("🔍 Looking up breakpoint with label key: {}", locationKey);
        BreakpointConfig breakpoint = breakpointsCache.get(locationKey);

        if (breakpoint == null) {
            String lineKey = fileName + ":" + lineNumber;
            logger.debug("🔍 Label key not found, trying line key: {}", lineKey);
            breakpoint = breakpointsCache.get(lineKey);
            logger.debug("Breakpoint not found by label key {}, trying line key {}: {}", locationKey, lineKey, breakpoint != null ? "found" : "not found");
        } else {
            logger.debug("✅ Breakpoint found by label key: {} (enabled={})", breakpoint.id, breakpoint.enabled);
        }

        if (breakpoint == null) {
            logger.debug("No breakpoint found in cache for {} or {}, skipping capture (cache size: {})", locationKey, fileName + ":" + lineNumber, breakpointsCache.size());
            logger.debug("🔍 Cache keys: {}", breakpointsCache.keySet());
            return;
        }

        if (!breakpoint.enabled) {
            logger.debug("Breakpoint {} exists but is disabled, skipping capture", breakpoint.id);
            return;
        }

        if (breakpoint.expireAt != null && Instant.now().isAfter(breakpoint.expireAt)) {
            logger.debug("⏰ Breakpoint {} expired at {}, skipping capture", breakpoint.id, breakpoint.expireAt);
            return;
        }

        if (breakpoint.maxCaptures > 0 && breakpoint.captureCount >= breakpoint.maxCaptures) {
            logger.debug("📊 Breakpoint {} reached max captures ({}/{}), skipping", breakpoint.id, breakpoint.captureCount, breakpoint.maxCaptures);
            return;
        }

        logger.debug("🚀 All checks passed, preparing to capture snapshot for breakpoint: {}", breakpoint.id);

        String stackTrace = Arrays.stream(Thread.currentThread().getStackTrace())
                .skip(2)
                .map(ste -> ste.toString())
                .collect(Collectors.joining("\n"));

        SpanContext spanContext = Span.current().getSpanContext();
        String traceId = spanContext.isValid() ? spanContext.getTraceId() : null;
        String spanId = spanContext.isValid() ? spanContext.getSpanId() : null;

        logger.debug("🔐 Scanning {} variables for security issues", variables.size());
        SecurityScanResult scanResult = scanForSecurityIssues(variables);
        logger.debug("🔐 Security scan complete: {} flags found", scanResult.securityFlags.size());

        Snapshot snapshot = new Snapshot(
            breakpoint.id,
            serviceName,
            fileName,
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

        logger.debug("📸 Submitting snapshot capture asynchronously for breakpoint: {}", breakpoint.id);
        CompletableFuture.runAsync(() -> {
            try {
                captureSnapshot(snapshot);
            } catch (Exception e) {
                logger.error("❌ Exception in async snapshot capture", e);
            }
        });
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
        logger.debug("📸 captureSnapshot called for breakpoint: {}", snapshot.breakpointId);
        try {
            String url = baseURL + "/sdk/snapshots/capture";
            logger.debug("📸 Serializing snapshot to JSON");
            String jsonPayload = gson.toJson(snapshot);
            logger.debug("📸 Snapshot JSON payload: {} bytes", jsonPayload.length());

            logger.debug("📸 Sending POST to: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-API-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                logger.info("📸 Snapshot captured: {}", snapshot.label != null ? snapshot.label : snapshot.filePath);
            } else {
                logger.error("⚠️  Failed to capture snapshot: HTTP {}", response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            logger.error("⚠️  Failed to capture snapshot", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private SecurityScanResult scanForSecurityIssues(Map<String, Object> variables) {
        List<SecurityFlag> securityFlags = new ArrayList<>();
        Map<String, Object> sanitized = new HashMap<>();

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            if (name.toLowerCase().matches(".*(password|secret|token|key|credential).*")) {
                securityFlags.add(new SecurityFlag(
                    "sensitive_variable_name",
                    "medium",
                    name
                ));
                sanitized.put(name, "[REDACTED]");
                continue;
            }

            String serialized = String.valueOf(value);
            List<SensitiveDataDetector.Finding> findings = securityDetector.scan(serialized);

            if (!findings.isEmpty()) {
                for (SensitiveDataDetector.Finding finding : findings) {
                    securityFlags.add(new SecurityFlag(
                        "sensitive_data_" + finding.getType().toLowerCase(),
                        finding.getSeverity().toLowerCase(),
                        name
                    ));
                }
                sanitized.put(name, "[REDACTED]");
            } else {
                sanitized.put(name, value);
            }
        }

        return new SecurityScanResult(sanitized, securityFlags);
    }

    static class BreakpointsResponse {
        public List<BreakpointConfig> breakpoints = new ArrayList<>();
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

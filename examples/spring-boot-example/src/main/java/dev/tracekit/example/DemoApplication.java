package dev.tracekit.example;

import dev.tracekit.TracekitSDK;
import dev.tracekit.metrics.Counter;
import dev.tracekit.metrics.Gauge;
import dev.tracekit.metrics.Histogram;
import dev.tracekit.security.SensitiveDataDetector;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Spring Boot demo application showcasing TraceKit APM integration.
 *
 * This application demonstrates:
 * - Automatic HTTP request tracing
 * - Error tracking and monitoring
 * - Distributed tracing with OpenTelemetry
 * - Zero-code instrumentation via auto-configuration
 */
@SpringBootApplication
@RestController
public class DemoApplication {

    private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);

    @Autowired
    private TracekitSDK tracekitSDK;

    @Autowired
    private UserRepository userRepository;

    // Metrics
    private Counter requestCounter;
    private Gauge activeRequestsGauge;
    private Histogram requestDurationHistogram;
    private Counter paymentCounter;
    private Histogram paymentAmountHistogram;

    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Autowired
    public void setTracekitSDK(TracekitSDK sdk) {
        this.tracekitSDK = sdk;
        logger.info("TraceKit SDK injected");

        // Initialize metrics
        this.requestCounter = sdk.counter("http.requests.total",
            Map.of("service", "demo-app"));
        this.activeRequestsGauge = sdk.gauge("http.requests.active");
        this.requestDurationHistogram = sdk.histogram("http.request.duration",
            Map.of("unit", "ms"));
        this.paymentCounter = sdk.counter("payments.total");
        this.paymentAmountHistogram = sdk.histogram("payment.amount",
            Map.of("currency", "usd"));

        logger.info("Metrics initialized");
    }

    /**
     * Welcome endpoint - demonstrates automatic request tracing via auto-instrumentation.
     */
    @GetMapping("/")
    public Map<String, Object> welcome(HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        activeRequests.incrementAndGet();
        activeRequestsGauge.set(activeRequests.get());

        try {
            logger.info("Welcome endpoint called");
            requestCounter.inc();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Welcome to TraceKit Spring Boot Example!");
            response.put("version", "1.0.0");
            response.put("features", new String[]{
                "Automatic HTTP request tracing",
                "Automatic JDBC query tracing (PostgreSQL)",
                "Error tracking and monitoring",
                "Distributed tracing with OpenTelemetry",
                "Zero-code instrumentation via auto-configuration"
            });
            response.put("endpoints", new String[]{
                "GET / - This welcome message",
                "GET /users/{id} - Get user by ID from database (automatic JDBC tracing)",
                "GET /error - Simulate an error for testing",
                "POST /scan - Scan code for security issues",
                "POST /process-payment - Process payment with snapshot capture",
                "GET /metrics - View current metrics"
            });

            return response;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            requestDurationHistogram.record(duration);
            activeRequests.decrementAndGet();
            activeRequestsGauge.set(activeRequests.get());
        }
    }

    /**
     * User lookup endpoint - demonstrates automatic JDBC tracing via auto-instrumentation.
     * Database queries are automatically traced without any manual instrumentation code.
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        activeRequests.incrementAndGet();
        activeRequestsGauge.set(activeRequests.get());

        try {
            logger.info("Looking up user with ID: {}", id);
            requestCounter.inc();

            // Simulate random processing delay
            simulateProcessing();

            // This query will be automatically traced by OpenTelemetry JDBC instrumentation
            // You'll see a span for the SQL SELECT statement in the trace
            Long userId;
            try {
                userId = Long.parseLong(id);
            } catch (NumberFormatException e) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid user ID format");
                error.put("userId", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            java.util.Optional<User> userOptional = userRepository.findById(userId);

            if (userOptional.isEmpty()) {
                logger.warn("User not found: {}", id);

                Map<String, String> error = new HashMap<>();
                error.put("error", "User not found");
                error.put("userId", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            User user = userOptional.get();
            logger.info("User found: {}", user.getName());
            return ResponseEntity.ok(user);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            requestDurationHistogram.record(duration);
            activeRequests.decrementAndGet();
            activeRequestsGauge.set(activeRequests.get());
        }
    }

    /**
     * Error endpoint - demonstrates automatic error tracking and exception handling.
     */
    @GetMapping("/error")
    public ResponseEntity<?> simulateError() {
        logger.error("Error endpoint called - simulating exception");

        // Simulate different types of errors randomly
        int errorType = ThreadLocalRandom.current().nextInt(3);

        RuntimeException exception;
        switch (errorType) {
            case 0:
                exception = new RuntimeException("Simulated runtime exception for testing");
                break;
            case 1:
                exception = new IllegalStateException("Simulated illegal state for testing");
                break;
            default:
                exception = new NullPointerException("Simulated null pointer exception for testing");
                break;
        }

        throw exception;
    }

    /**
     * Security scan endpoint - demonstrates sensitive data detection.
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanCode(@RequestBody Map<String, String> request) {
        logger.info("Security scan endpoint called");

        String code = request.get("code");
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
        }

        SensitiveDataDetector detector = new SensitiveDataDetector();
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        Map<String, Object> response = new HashMap<>();
        response.put("findings_count", findings.size());
        response.put("findings", findings.stream()
                .map(f -> Map.of(
                        "type", f.getType(),
                        "severity", f.getSeverity(),
                        "line", f.getLine(),
                        "column", f.getColumn(),
                        "message", f.getMessage()
                ))
                .collect(Collectors.toList()));

        String redacted = detector.redact(code);
        response.put("redacted_code", redacted);

        logger.info("Found {} security issues", findings.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Payment processing endpoint - demonstrates snapshot capture with security scanning.
     * This shows how security issues (like hardcoded API keys) are automatically detected
     * and linked to traces when code monitoring is enabled.
     */
    @PostMapping("/process-payment")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        activeRequests.incrementAndGet();
        activeRequestsGauge.set(activeRequests.get());

        try {
            logger.info("Payment processing endpoint called");
            requestCounter.inc();

            String amount = String.valueOf(request.get("amount"));
            String currency = String.valueOf(request.get("currency"));

            // Track payment metrics
            paymentCounter.inc();
            try {
                double amountValue = Double.parseDouble(amount);
                paymentAmountHistogram.record(amountValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid amount format: {}", amount);
            }

            String stripeApiKey = "sk_test_FakeKey123456789ABCDEFGHIJKL";
            String customerId = "cus_123456789";
            String cardNumber = "4532123456789012";

            Map<String, Object> capturedVars = new HashMap<>();
            capturedVars.put("amount", amount);
            capturedVars.put("currency", currency);
            capturedVars.put("stripeApiKey", stripeApiKey);
            capturedVars.put("customerId", customerId);
            capturedVars.put("cardNumber", cardNumber);
            capturedVars.put("timestamp", System.currentTimeMillis());

            tracekitSDK.captureSnapshot("payment-processing", capturedVars);

            simulateProcessing();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("transaction_id", "txn_" + System.currentTimeMillis());
            response.put("amount", amount);
            response.put("currency", currency);
            response.put("message", "Payment processed successfully");

            logger.info("Payment processed successfully");

            return ResponseEntity.ok(response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            requestDurationHistogram.record(duration);
            activeRequests.decrementAndGet();
            activeRequestsGauge.set(activeRequests.get());
        }
    }

    /**
     * Metrics endpoint - displays current metrics summary.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        logger.info("Metrics endpoint called");

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Metrics are being collected and sent to TraceKit");
        response.put("active_requests", activeRequests.get());
        response.put("metrics_tracked", Map.of(
            "http.requests.total", "Counter - Total HTTP requests",
            "http.requests.active", "Gauge - Currently active requests",
            "http.request.duration", "Histogram - Request duration in ms",
            "payments.total", "Counter - Total payments processed",
            "payment.amount", "Histogram - Payment amounts in USD"
        ));
        response.put("note", "Metrics are flushed every 10 seconds or when 100 metrics are collected");

        return ResponseEntity.ok(response);
    }

    /**
     * Simulates processing delay to make traces more interesting.
     */
    private void simulateProcessing() {
        try {
            // Random delay between 50-200ms
            int delay = ThreadLocalRandom.current().nextInt(50, 201);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Processing simulation interrupted", e);
        }
    }
}

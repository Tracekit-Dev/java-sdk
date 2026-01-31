package dev.tracekit.example;

import dev.tracekit.TracekitSDK;
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

    // Simulated user database
    private static final Map<String, User> USERS = new HashMap<>();

    static {
        USERS.put("1", new User("1", "Alice Johnson", "alice@example.com"));
        USERS.put("2", new User("2", "Bob Smith", "bob@example.com"));
        USERS.put("3", new User("3", "Charlie Brown", "charlie@example.com"));
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Autowired
    public void setTracekitSDK(TracekitSDK sdk) {
        this.tracekitSDK = sdk;
        logger.info("TraceKit SDK injected");
    }

    /**
     * Welcome endpoint - demonstrates automatic request tracing via auto-instrumentation.
     */
    @GetMapping("/")
    public Map<String, Object> welcome(HttpServletRequest request) {
        logger.info("Welcome endpoint called");

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to TraceKit Spring Boot Example!");
        response.put("version", "1.0.0");
        response.put("endpoints", new String[]{
            "GET / - This welcome message",
            "GET /users/{id} - Get user by ID (try 1, 2, or 3)",
            "GET /error - Simulate an error for testing",
            "POST /scan - Scan code for security issues",
            "POST /process-payment - Process payment with snapshot capture"
        });

        return response;
    }

    /**
     * User lookup endpoint - demonstrates automatic tracing with parameters and conditional logic.
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id, HttpServletRequest request) {
        logger.info("Looking up user with ID: {}", id);

        // Simulate random processing delay
        simulateProcessing();

        User user = USERS.get(id);
        if (user == null) {
            logger.warn("User not found: {}", id);

            Map<String, String> error = new HashMap<>();
            error.put("error", "User not found");
            error.put("userId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        logger.info("User found: {}", user.getName());
        return ResponseEntity.ok(user);
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
        logger.info("Payment processing endpoint called");

        String amount = String.valueOf(request.get("amount"));
        String currency = String.valueOf(request.get("currency"));

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

    /**
     * Simple User model for demonstration.
     */
    public static class User {
        private String id;
        private String name;
        private String email;

        public User(String id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}

package dev.tracekit.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * Welcome endpoint - demonstrates basic request tracing.
     */
    @GetMapping("/")
    public Map<String, Object> welcome() {
        logger.info("Welcome endpoint called");

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to TraceKit Spring Boot Example!");
        response.put("version", "1.0.0");
        response.put("endpoints", new String[]{
            "GET / - This welcome message",
            "GET /users/{id} - Get user by ID (try 1, 2, or 3)",
            "GET /error - Simulate an error for testing"
        });

        return response;
    }

    /**
     * User lookup endpoint - demonstrates tracing with parameters and conditional logic.
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
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
     * Error endpoint - demonstrates error tracking and exception handling.
     */
    @GetMapping("/error")
    public ResponseEntity<?> simulateError() {
        logger.error("Error endpoint called - simulating exception");

        // Simulate different types of errors randomly
        int errorType = ThreadLocalRandom.current().nextInt(3);

        switch (errorType) {
            case 0:
                throw new RuntimeException("Simulated runtime exception for testing");
            case 1:
                throw new IllegalStateException("Simulated illegal state for testing");
            default:
                throw new NullPointerException("Simulated null pointer exception for testing");
        }
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

package dev.tracekit.local;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocalUIDetectorTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testSuccessfulLocalUIDetection() throws InterruptedException {
        // Arrange: Mock server responds with 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act
        boolean isAvailable = detector.isLocalUIAvailable();
        String endpoint = detector.getLocalUIEndpoint();

        // Assert
        assertTrue(isAvailable, "Local UI should be detected as available");
        assertNotNull(endpoint, "Endpoint should not be null");
        assertEquals("http://localhost:" + port + "/v1/traces", endpoint);

        // Verify the health check request was made
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/health", request.getPath());
    }

    @Test
    void testFailedDetectionServerNotRunning() {
        // Arrange: Use a port where no server is running
        int unusedPort = 19999;
        LocalUIDetector detector = new LocalUIDetector(unusedPort);

        // Act
        boolean isAvailable = detector.isLocalUIAvailable();
        String endpoint = detector.getLocalUIEndpoint();

        // Assert
        assertFalse(isAvailable, "Local UI should not be detected");
        assertNull(endpoint, "Endpoint should be null when not available");
    }

    @Test
    void testCachingOfDetectionResult() throws InterruptedException {
        // Arrange: Mock server responds with 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act: Call detection multiple times
        boolean firstCall = detector.isLocalUIAvailable();
        boolean secondCall = detector.isLocalUIAvailable();
        String firstEndpoint = detector.getLocalUIEndpoint();
        String secondEndpoint = detector.getLocalUIEndpoint();

        // Assert: Both calls return the same result
        assertTrue(firstCall);
        assertTrue(secondCall);
        assertNotNull(firstEndpoint);
        assertEquals(firstEndpoint, secondEndpoint);

        // Verify only ONE HTTP request was made (caching works)
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "First request should be made");

        // Wait a bit and verify no second request was made
        RecordedRequest secondRequest = mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS);
        assertNull(secondRequest, "Second request should not be made due to caching");
    }

    @Test
    void testDefaultPortUsage() {
        // Act: Create detector with default constructor
        LocalUIDetector detector = new LocalUIDetector();

        // Assert: Should use default port 9999
        // We can't check availability (no server running on 9999)
        // but we can verify the endpoint format
        String endpoint = detector.getLocalUIEndpoint();

        // If not available, endpoint should be null
        // This is expected since no server is running on default port
        assertNull(endpoint, "Endpoint should be null when server not running on default port");
    }

    @Test
    void testCustomPortUsage() throws InterruptedException {
        // Arrange: Mock server on custom port
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        int customPort = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(customPort);

        // Act
        String endpoint = detector.getLocalUIEndpoint();

        // Assert: Endpoint should use custom port
        assertNotNull(endpoint);
        assertTrue(endpoint.contains(":" + customPort),
                "Endpoint should contain custom port: " + customPort);
        assertEquals("http://localhost:" + customPort + "/v1/traces", endpoint);
    }

    @Test
    void testEndpointURLFormat() throws InterruptedException {
        // Arrange: Mock server responds with 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act
        String endpoint = detector.getLocalUIEndpoint();

        // Assert: Verify exact endpoint format
        assertNotNull(endpoint);
        assertEquals("http://localhost:" + port + "/v1/traces", endpoint,
                "Endpoint should follow the format: http://localhost:{port}/v1/traces");
        assertTrue(endpoint.startsWith("http://localhost:"));
        assertTrue(endpoint.endsWith("/v1/traces"));
    }

    @Test
    void testHealthCheckEndpointPath() throws InterruptedException {
        // Arrange: Mock server responds with 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act
        detector.isLocalUIAvailable();

        // Assert: Verify the health check endpoint path
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/health", request.getPath(),
                "Health check should use /api/health endpoint");
    }

    @Test
    void testTimeoutHandling() {
        // Arrange: Mock server that delays response beyond timeout
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .setBodyDelay(1, TimeUnit.SECONDS)); // Delay > 500ms timeout

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act & Assert: Should handle timeout gracefully
        long startTime = System.currentTimeMillis();
        boolean isAvailable = detector.isLocalUIAvailable();
        long duration = System.currentTimeMillis() - startTime;

        // Should timeout and return false
        assertFalse(isAvailable, "Should timeout and return false");
        assertTrue(duration < 2000, "Should timeout quickly (< 2s), actual: " + duration + "ms");
    }

    @Test
    void testNon200ResponseCode() throws InterruptedException {
        // Arrange: Mock server responds with 404
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act
        boolean isAvailable = detector.isLocalUIAvailable();
        String endpoint = detector.getLocalUIEndpoint();

        // Assert: Should treat non-200 as unavailable
        assertFalse(isAvailable, "Should be unavailable for non-200 response");
        assertNull(endpoint, "Endpoint should be null for non-200 response");
    }

    @Test
    void testThreadSafeCaching() throws InterruptedException {
        // Arrange: Mock server responds with 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        int port = mockWebServer.getPort();
        LocalUIDetector detector = new LocalUIDetector(port);

        // Act: Access from multiple threads
        Thread thread1 = new Thread(() -> detector.isLocalUIAvailable());
        Thread thread2 = new Thread(() -> detector.isLocalUIAvailable());
        Thread thread3 = new Thread(() -> detector.getLocalUIEndpoint());

        thread1.start();
        thread2.start();
        thread3.start();

        thread1.join();
        thread2.join();
        thread3.join();

        // Assert: Only one HTTP request should have been made
        int requestCount = mockWebServer.getRequestCount();
        assertEquals(1, requestCount,
                "Only one HTTP request should be made even with concurrent access");
    }
}

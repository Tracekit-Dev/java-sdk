package dev.tracekit;

import com.google.gson.Gson;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TracekitClientTest {

    private MockWebServer cloudServer;
    private MockWebServer localServer;
    private TracekitConfig config;
    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        cloudServer = new MockWebServer();
        cloudServer.start();

        localServer = new MockWebServer();
        localServer.start();

        gson = new Gson();

        config = TracekitConfig.builder()
                .apiKey("test-api-key-123")
                .serviceName("test-service")
                .endpoint(cloudServer.url("/v1/traces").toString())
                .environment("test")
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        cloudServer.shutdown();
        localServer.shutdown();
    }

    @Test
    void testSendTraceToCloudOnly() throws Exception {
        // Given: Cloud server responds successfully, no local endpoint
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        TracekitClient client = new TracekitClient(config, null);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should succeed
        assertTrue(result, "Should return true when cloud succeeds");

        // Verify cloud request
        RecordedRequest cloudRequest = cloudServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(cloudRequest);
        assertEquals("POST", cloudRequest.getMethod());
        assertEquals("/v1/traces", cloudRequest.getPath());
        assertEquals("test-api-key-123", cloudRequest.getHeader("X-API-Key"));
        assertNotNull(cloudRequest.getHeader("User-Agent"));
        assertTrue(cloudRequest.getHeader("User-Agent").startsWith("tracekit-java-sdk/"));
        assertTrue(cloudRequest.getHeader("Content-Type").startsWith("application/json"),
                "Content-Type should be application/json");

        // Verify JSON body
        String body = cloudRequest.getBody().readUtf8();
        Map<String, Object> sentData = gson.fromJson(body, Map.class);
        assertEquals("test-trace-id", sentData.get("traceId"));
        assertEquals("test-span-id", sentData.get("spanId"));
    }

    @Test
    void testSendTraceToBothCloudAndLocal() throws Exception {
        // Given: Both servers respond successfully
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));
        localServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        String localEndpoint = localServer.url("/traces").toString();
        TracekitClient client = new TracekitClient(config, localEndpoint);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should succeed
        assertTrue(result, "Should return true when both succeed");

        // Verify cloud request
        RecordedRequest cloudRequest = cloudServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(cloudRequest);
        assertEquals("POST", cloudRequest.getMethod());
        assertEquals("test-api-key-123", cloudRequest.getHeader("X-API-Key"));
        assertNotNull(cloudRequest.getHeader("User-Agent"));

        // Verify local request
        RecordedRequest localRequest = localServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(localRequest);
        assertEquals("POST", localRequest.getMethod());
        assertEquals("/traces", localRequest.getPath());
        assertNull(localRequest.getHeader("X-API-Key"), "Local should not have API key");
        assertNotNull(localRequest.getHeader("User-Agent"));
        assertTrue(localRequest.getHeader("User-Agent").startsWith("tracekit-java-sdk/"));
        assertTrue(localRequest.getHeader("Content-Type").startsWith("application/json"),
                "Content-Type should be application/json");

        // Verify same data sent to both
        String cloudBody = cloudRequest.getBody().readUtf8();
        String localBody = localRequest.getBody().readUtf8();
        assertEquals(cloudBody, localBody, "Same trace data should be sent to both endpoints");
    }

    @Test
    void testCloudFailureButLocalSuccess() throws Exception {
        // Given: Cloud fails, local succeeds
        cloudServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"Internal Server Error\"}"));
        localServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        String localEndpoint = localServer.url("/traces").toString();
        TracekitClient client = new TracekitClient(config, localEndpoint);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should still succeed (at least one endpoint succeeded)
        assertTrue(result, "Should return true when local succeeds even if cloud fails");

        // Verify both requests were made
        assertNotNull(cloudServer.takeRequest(1, TimeUnit.SECONDS));
        assertNotNull(localServer.takeRequest(1, TimeUnit.SECONDS));
    }

    @Test
    void testLocalFailureButCloudSuccess() throws Exception {
        // Given: Cloud succeeds, local fails
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));
        localServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"Internal Server Error\"}"));

        String localEndpoint = localServer.url("/traces").toString();
        TracekitClient client = new TracekitClient(config, localEndpoint);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should succeed (cloud succeeded)
        assertTrue(result, "Should return true when cloud succeeds even if local fails");

        // Verify both requests were made
        assertNotNull(cloudServer.takeRequest(1, TimeUnit.SECONDS));
        assertNotNull(localServer.takeRequest(1, TimeUnit.SECONDS));
    }

    @Test
    void testBothEndpointsFail() throws Exception {
        // Given: Both endpoints fail
        cloudServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"Cloud error\"}"));
        localServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"Local error\"}"));

        String localEndpoint = localServer.url("/traces").toString();
        TracekitClient client = new TracekitClient(config, localEndpoint);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should fail
        assertFalse(result, "Should return false when both endpoints fail");

        // Verify both requests were made
        assertNotNull(cloudServer.takeRequest(1, TimeUnit.SECONDS));
        assertNotNull(localServer.takeRequest(1, TimeUnit.SECONDS));
    }

    @Test
    void testCloudOnlyFails() throws Exception {
        // Given: Cloud fails, no local endpoint
        cloudServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"Cloud error\"}"));

        TracekitClient client = new TracekitClient(config, null);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should fail
        assertFalse(result, "Should return false when cloud fails and no local endpoint");

        // Verify cloud request was made
        assertNotNull(cloudServer.takeRequest(1, TimeUnit.SECONDS));
    }

    @Test
    void testProperHeadersCloudOnly() throws Exception {
        // Given: Cloud server
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        TracekitClient client = new TracekitClient(config, null);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        client.sendTrace(traceData);

        // Then: Verify headers
        RecordedRequest request = cloudServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);

        // X-API-Key header
        assertEquals("test-api-key-123", request.getHeader("X-API-Key"));

        // User-Agent header
        String userAgent = request.getHeader("User-Agent");
        assertNotNull(userAgent);
        assertTrue(userAgent.startsWith("tracekit-java-sdk/"), "User-Agent should start with tracekit-java-sdk/");

        // Content-Type header
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"),
                "Content-Type should be application/json");
    }

    @Test
    void testProperHeadersLocalOnly() throws Exception {
        // Given: Local server, cloud will fail
        cloudServer.enqueue(new MockResponse().setResponseCode(500));
        localServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        String localEndpoint = localServer.url("/traces").toString();
        TracekitClient client = new TracekitClient(config, localEndpoint);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        client.sendTrace(traceData);

        // Then: Verify local headers (skip cloud request)
        cloudServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest localRequest = localServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(localRequest);

        // No X-API-Key header for local
        assertNull(localRequest.getHeader("X-API-Key"), "Local endpoint should not receive API key");

        // User-Agent header
        String userAgent = localRequest.getHeader("User-Agent");
        assertNotNull(userAgent);
        assertTrue(userAgent.startsWith("tracekit-java-sdk/"), "User-Agent should start with tracekit-java-sdk/");

        // Content-Type header
        assertTrue(localRequest.getHeader("Content-Type").startsWith("application/json"),
                "Content-Type should be application/json");
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given: Cloud server
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        TracekitClient client = new TracekitClient(config, null);

        // Complex trace data with nested objects
        Map<String, Object> traceData = new HashMap<>();
        traceData.put("traceId", "test-trace-123");
        traceData.put("spanId", "test-span-456");
        traceData.put("parentSpanId", "parent-span-789");
        traceData.put("name", "test-operation");
        traceData.put("timestamp", 1234567890L);
        traceData.put("duration", 100L);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("http.method", "GET");
        attributes.put("http.status_code", 200);
        attributes.put("service.name", "test-service");
        traceData.put("attributes", attributes);

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Verify JSON
        assertTrue(result);
        RecordedRequest request = cloudServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);

        String body = request.getBody().readUtf8();
        Map<String, Object> sentData = gson.fromJson(body, Map.class);

        assertEquals("test-trace-123", sentData.get("traceId"));
        assertEquals("test-span-456", sentData.get("spanId"));
        assertEquals("parent-span-789", sentData.get("parentSpanId"));
        assertEquals("test-operation", sentData.get("name"));
        assertEquals(1.23456789E9, sentData.get("timestamp")); // Gson converts to double
        assertEquals(100.0, sentData.get("duration"));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentAttributes = (Map<String, Object>) sentData.get("attributes");
        assertNotNull(sentAttributes);
        assertEquals("GET", sentAttributes.get("http.method"));
        assertEquals(200.0, sentAttributes.get("http.status_code"));
        assertEquals("test-service", sentAttributes.get("service.name"));
    }

    @Test
    void testTimeoutHandling() throws Exception {
        // Given: Cloud server delays response beyond timeout
        // Use setSocketPolicy to simulate a socket timeout rather than setBodyDelay
        cloudServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)); // Socket never responds

        TracekitClient client = new TracekitClient(config, null);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        long startTime = System.currentTimeMillis();
        boolean result = client.sendTrace(traceData);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should fail due to timeout
        assertFalse(result, "Should return false on timeout");

        // Should timeout around 10 seconds (read timeout)
        assertTrue(duration < 13000, "Should timeout within 13 seconds");
        assertTrue(duration >= 9000, "Should wait at least 9 seconds (near read timeout)");
    }

    @Test
    void testNullTraceDataHandling() {
        // Given: Client with valid config
        TracekitClient client = new TracekitClient(config, null);

        // When/Then: Should handle null gracefully
        assertThrows(NullPointerException.class, () -> client.sendTrace(null),
                "Should throw NullPointerException for null trace data");
    }

    @Test
    void testEmptyTraceDataHandling() throws Exception {
        // Given: Cloud server
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        TracekitClient client = new TracekitClient(config, null);
        Map<String, Object> emptyTraceData = new HashMap<>();

        // When: Send empty trace
        boolean result = client.sendTrace(emptyTraceData);

        // Then: Should succeed (server accepted it)
        assertTrue(result);

        RecordedRequest request = cloudServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);

        String body = request.getBody().readUtf8();
        assertEquals("{}", body, "Empty map should serialize to empty JSON object");
    }

    @Test
    void testNullConfigHandling() {
        // When/Then: Should throw exception for null config
        assertThrows(NullPointerException.class, () -> new TracekitClient(null, null),
                "Should throw NullPointerException for null config");
    }

    @Test
    void testEmptyLocalEndpointTreatedAsNull() throws Exception {
        // Given: Empty string for local endpoint
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        TracekitClient client = new TracekitClient(config, "");
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should only send to cloud
        assertTrue(result);
        assertNotNull(cloudServer.takeRequest(1, TimeUnit.SECONDS));

        // Local server should not receive request
        assertNull(localServer.takeRequest(100, TimeUnit.MILLISECONDS),
                "Empty local endpoint should be treated as null, no request to local");
    }

    @Test
    void testWhitespaceLocalEndpointTreatedAsNull() throws Exception {
        // Given: Whitespace string for local endpoint
        cloudServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        TracekitClient client = new TracekitClient(config, "   ");
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        boolean result = client.sendTrace(traceData);

        // Then: Should only send to cloud
        assertTrue(result);
        assertNotNull(cloudServer.takeRequest(1, TimeUnit.SECONDS));

        // Local server should not receive request
        assertNull(localServer.takeRequest(100, TimeUnit.MILLISECONDS),
                "Whitespace local endpoint should be treated as null, no request to local");
    }

    @Test
    void testConnectionTimeout() throws Exception {
        // Given: Use an unreachable endpoint (connection will timeout)
        TracekitConfig unreachableConfig = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .endpoint("http://10.255.255.1:9999/traces") // Non-routable IP
                .build();

        TracekitClient client = new TracekitClient(unreachableConfig, null);
        Map<String, Object> traceData = createTestTraceData();

        // When: Send trace
        long startTime = System.currentTimeMillis();
        boolean result = client.sendTrace(traceData);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should fail due to connection timeout
        assertFalse(result, "Should return false on connection timeout");

        // Should timeout around 5 seconds (connect timeout)
        assertTrue(duration < 8000, "Should timeout within 8 seconds");
        assertTrue(duration >= 4000, "Should wait at least 4 seconds (near connect timeout)");
    }

    private Map<String, Object> createTestTraceData() {
        Map<String, Object> traceData = new HashMap<>();
        traceData.put("traceId", "test-trace-id");
        traceData.put("spanId", "test-span-id");
        traceData.put("name", "test-operation");
        traceData.put("timestamp", System.currentTimeMillis());
        return traceData;
    }
}

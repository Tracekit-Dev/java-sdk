package dev.tracekit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test cases for URL resolution logic in TraceKit Java SDK.
 * Ensures consistent endpoint resolution across various input formats.
 */
public class UrlResolutionTest {

    // Just host cases
    @Test
    public void testJustHostWithSSL() {
        String result = TracekitSDK.resolveEndpoint("app.tracekit.dev", "/v1/traces", true);
        assertEquals("https://app.tracekit.dev/v1/traces", result);
    }

    @Test
    public void testJustHostWithoutSSL() {
        String result = TracekitSDK.resolveEndpoint("localhost:8081", "/v1/traces", false);
        assertEquals("http://localhost:8081/v1/traces", result);
    }

    @Test
    public void testJustHostWithTrailingSlash() {
        String result = TracekitSDK.resolveEndpoint("app.tracekit.dev/", "/v1/metrics", true);
        assertEquals("https://app.tracekit.dev/v1/metrics", result);
    }

    // Host with scheme cases
    @Test
    public void testHttpWithHostOnly() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081", "/v1/traces", true);
        assertEquals("http://localhost:8081/v1/traces", result);
    }

    @Test
    public void testHttpsWithHostOnly() {
        String result = TracekitSDK.resolveEndpoint("https://app.tracekit.dev", "/v1/metrics", false);
        assertEquals("https://app.tracekit.dev/v1/metrics", result);
    }

    @Test
    public void testHttpWithHostAndTrailingSlash() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081/", "/v1/traces", true);
        assertEquals("http://localhost:8081/v1/traces", result);
    }

    // Full URL cases
    @Test
    public void testFullUrlWithStandardPath() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081/v1/traces", "/v1/traces", true);
        assertEquals("http://localhost:8081/v1/traces", result);
    }

    @Test
    public void testFullUrlWithCustomPath() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081/custom/path", "/v1/traces", true);
        assertEquals("http://localhost:8081/v1/traces", result);
    }

    @Test
    public void testFullUrlWithTrailingSlash() {
        String result = TracekitSDK.resolveEndpoint("https://app.tracekit.dev/api/v2/", "/v1/traces", false);
        assertEquals("https://app.tracekit.dev/v1/traces", result);
    }

    // Edge cases
    @Test
    public void testEmptyPathForSnapshots() {
        String result = TracekitSDK.resolveEndpoint("app.tracekit.dev", "", true);
        assertEquals("https://app.tracekit.dev", result);
    }

    @Test
    public void testHttpWithEmptyPath() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081", "", true);
        assertEquals("http://localhost:8081", result);
    }

    @Test
    public void testHttpWithTrailingSlashAndEmptyPath() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081/", "", true);
        assertEquals("http://localhost:8081", result);
    }

    @Test
    public void testSnapshotWithFullUrlExtractsBaseHttp() {
        String result = TracekitSDK.resolveEndpoint("http://localhost:8081/v1/traces", "", true);
        assertEquals("http://localhost:8081", result);
    }

    @Test
    public void testSnapshotWithFullUrlExtractsBaseHttps() {
        String result = TracekitSDK.resolveEndpoint("https://app.tracekit.dev/v1/traces", "", false);
        assertEquals("https://app.tracekit.dev", result);
    }

    // ExtractBaseURL tests
    @Test
    public void testExtractBaseFromTracesEndpointHttp() {
        String result = TracekitSDK.extractBaseURL("http://localhost:8081/v1/traces");
        assertEquals("http://localhost:8081", result);
    }

    @Test
    public void testExtractBaseFromTracesEndpointHttps() {
        String result = TracekitSDK.extractBaseURL("https://app.tracekit.dev/v1/traces");
        assertEquals("https://app.tracekit.dev", result);
    }

    @Test
    public void testExtractBaseFromMetricsEndpoint() {
        String result = TracekitSDK.extractBaseURL("https://app.tracekit.dev/v1/metrics");
        assertEquals("https://app.tracekit.dev", result);
    }

    @Test
    public void testExtractBaseFromCustomPath() {
        String result = TracekitSDK.extractBaseURL("http://localhost:8081/custom");
        assertEquals("http://localhost:8081", result);
    }

    @Test
    public void testExtractBaseFromApiPath() {
        String result = TracekitSDK.extractBaseURL("http://localhost:8081/api");
        assertEquals("http://localhost:8081", result);
    }

    @Test
    public void testExtractFromApiV1TracesPath() {
        String result = TracekitSDK.extractBaseURL("https://app.tracekit.dev/api/v1/traces");
        assertEquals("https://app.tracekit.dev", result);
    }

    @Test
    public void testExtractFromApiV1MetricsPath() {
        String result = TracekitSDK.extractBaseURL("https://app.tracekit.dev/api/v1/metrics");
        assertEquals("https://app.tracekit.dev", result);
    }

    @Test
    public void testReturnAsIsWhenNoPathComponent() {
        String result = TracekitSDK.extractBaseURL("https://app.tracekit.dev");
        assertEquals("https://app.tracekit.dev", result);
    }

    // Integration tests
    @ParameterizedTest(name = "{0}")
    @MethodSource("provideEndpointResolutionScenarios")
    public void testEndpointResolutionScenarios(
            String scenarioName,
            String endpoint,
            String tracesPath,
            String metricsPath,
            boolean useSSL,
            String expectedTraces,
            String expectedMetrics,
            String expectedSnapshots
    ) {
        String tracesEndpoint = TracekitSDK.resolveEndpoint(endpoint, tracesPath, useSSL);
        String metricsEndpoint = TracekitSDK.resolveEndpoint(endpoint, metricsPath, useSSL);
        String snapshotEndpoint = TracekitSDK.resolveEndpoint(endpoint, "", useSSL);

        assertEquals(expectedTraces, tracesEndpoint, "Traces endpoint mismatch");
        assertEquals(expectedMetrics, metricsEndpoint, "Metrics endpoint mismatch");
        assertEquals(expectedSnapshots, snapshotEndpoint, "Snapshots endpoint mismatch");
    }

    private static Stream<Arguments> provideEndpointResolutionScenarios() {
        return Stream.of(
                Arguments.of(
                        "default production config",
                        "app.tracekit.dev",
                        "/v1/traces",
                        "/v1/metrics",
                        true,
                        "https://app.tracekit.dev/v1/traces",
                        "https://app.tracekit.dev/v1/metrics",
                        "https://app.tracekit.dev"
                ),
                Arguments.of(
                        "local development",
                        "localhost:8080",
                        "/v1/traces",
                        "/v1/metrics",
                        false,
                        "http://localhost:8080/v1/traces",
                        "http://localhost:8080/v1/metrics",
                        "http://localhost:8080"
                ),
                Arguments.of(
                        "custom paths",
                        "app.tracekit.dev",
                        "/api/v2/traces",
                        "/api/v2/metrics",
                        true,
                        "https://app.tracekit.dev/api/v2/traces",
                        "https://app.tracekit.dev/api/v2/metrics",
                        "https://app.tracekit.dev"
                ),
                Arguments.of(
                        "full URLs provided",
                        "http://localhost:8081/custom",
                        "/v1/traces",
                        "/v1/metrics",
                        true, // Should be ignored
                        "http://localhost:8081/v1/traces",
                        "http://localhost:8081/v1/metrics",
                        "http://localhost:8081"
                ),
                Arguments.of(
                        "trailing slash handling",
                        "http://localhost:8081/",
                        "/v1/traces",
                        "/v1/metrics",
                        false,
                        "http://localhost:8081/v1/traces",
                        "http://localhost:8081/v1/metrics",
                        "http://localhost:8081"
                ),
                Arguments.of(
                        "full URL with path - snapshots extract base",
                        "http://localhost:8081/v1/traces",
                        "/v1/traces",
                        "/v1/metrics",
                        true, // Should be ignored
                        "http://localhost:8081/v1/traces",
                        "http://localhost:8081/v1/metrics",
                        "http://localhost:8081" // Should extract base URL
                )
        );
    }
}

package dev.tracekit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TracekitConfigTest {

    @Test
    void testBuilderWithRequiredFields() {
        TracekitConfig config = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .build();

        assertEquals("test-api-key", config.getApiKey());
        assertEquals("test-service", config.getServiceName());
    }

    @Test
    void testDefaultValues() {
        TracekitConfig config = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .build();

        assertEquals("https://app.tracekit.dev/v1/traces", config.getEndpoint());
        assertEquals("production", config.getEnvironment());
        assertEquals(9999, config.getLocalUIPort());
        assertFalse(config.isEnableCodeMonitoring());
        assertFalse(config.isEnableSecurityScanning());
    }

    @Test
    void testOverridingDefaults() {
        TracekitConfig config = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .endpoint("https://custom.endpoint.com")
                .environment("staging")
                .localUIPort(8080)
                .enableCodeMonitoring(true)
                .enableSecurityScanning(true)
                .build();

        assertEquals("https://custom.endpoint.com", config.getEndpoint());
        assertEquals("staging", config.getEnvironment());
        assertEquals(8080, config.getLocalUIPort());
        assertTrue(config.isEnableCodeMonitoring());
        assertTrue(config.isEnableSecurityScanning());
    }

    @Test
    void testMissingApiKeyThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            TracekitConfig.builder()
                    .serviceName("test-service")
                    .build();
        });

        assertTrue(exception.getMessage().contains("apiKey"));
    }

    @Test
    void testMissingServiceNameThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            TracekitConfig.builder()
                    .apiKey("test-api-key")
                    .build();
        });

        assertTrue(exception.getMessage().contains("serviceName"));
    }

    @Test
    void testNullApiKeyThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            TracekitConfig.builder()
                    .apiKey(null)
                    .serviceName("test-service")
                    .build();
        });

        assertTrue(exception.getMessage().contains("apiKey"));
    }

    @Test
    void testNullServiceNameThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            TracekitConfig.builder()
                    .apiKey("test-api-key")
                    .serviceName(null)
                    .build();
        });

        assertTrue(exception.getMessage().contains("serviceName"));
    }

    @Test
    void testEmptyApiKeyThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            TracekitConfig.builder()
                    .apiKey("")
                    .serviceName("test-service")
                    .build();
        });

        assertTrue(exception.getMessage().contains("apiKey"));
    }

    @Test
    void testEmptyServiceNameThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            TracekitConfig.builder()
                    .apiKey("test-api-key")
                    .serviceName("")
                    .build();
        });

        assertTrue(exception.getMessage().contains("serviceName"));
    }

    @Test
    void testNullEnvironmentDefaultsToProduction() {
        TracekitConfig config = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .environment(null)
                .build();

        assertEquals("production", config.getEnvironment());
    }

    @Test
    void testEmptyEnvironmentDefaultsToProduction() {
        TracekitConfig config = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .environment("")
                .build();

        assertEquals("production", config.getEnvironment());
    }

    @Test
    void testConfigIsImmutable() {
        TracekitConfig config = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("test-service")
                .build();

        assertEquals("test-api-key", config.getApiKey());
        assertEquals("test-service", config.getServiceName());
    }
}

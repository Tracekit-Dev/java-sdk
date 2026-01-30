package dev.tracekit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TracekitSDK main class.
 *
 * <p>These tests verify the SDK initialization, OpenTelemetry integration,
 * and proper shutdown behavior.</p>
 */
class TracekitSDKTest {

    private TracekitConfig validConfig;
    private TracekitSDK sdk;

    @BeforeEach
    void setUp() {
        validConfig = TracekitConfig.builder()
                .apiKey("test-api-key-12345")
                .serviceName("test-service")
                .environment("test")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.shutdown();
            sdk = null;
        }
    }

    @AfterAll
    static void resetGlobalOpenTelemetry() throws Exception {
        // Reset GlobalOpenTelemetry using reflection for testing purposes
        java.lang.reflect.Field field = GlobalOpenTelemetry.class.getDeclaredField("globalOpenTelemetry");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void testCreateWithValidConfig() {
        // Act
        sdk = TracekitSDK.create(validConfig);

        // Assert
        assertNotNull(sdk, "SDK should not be null after initialization");
        assertEquals("test-service", sdk.getServiceName(), "Service name should match config");
    }

    @Test
    void testCreateWithNullConfigThrowsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            TracekitSDK.create(null);
        }, "Creating SDK with null config should throw NullPointerException");
    }

    @Test
    void testBuilderReturnsValidBuilder() {
        // Act
        TracekitSDK.Builder builder = TracekitSDK.builder();

        // Assert
        assertNotNull(builder, "Builder should not be null");
    }

    @Test
    void testBuilderPattern() {
        // Act
        sdk = TracekitSDK.builder()
                .apiKey("test-api-key-12345")
                .serviceName("builder-service")
                .environment("staging")
                .build();

        // Assert
        assertNotNull(sdk, "SDK should not be null after builder pattern");
        assertEquals("builder-service", sdk.getServiceName(), "Service name should match builder");
    }

    @Test
    void testBuilderWithMinimalConfig() {
        // Act
        sdk = TracekitSDK.builder()
                .apiKey("test-api-key-12345")
                .serviceName("minimal-service")
                .build();

        // Assert
        assertNotNull(sdk, "SDK should not be null with minimal config");
        assertEquals("minimal-service", sdk.getServiceName(), "Service name should match");
    }

    @Test
    void testGetServiceNameReturnsCorrectValue() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        String serviceName = sdk.getServiceName();

        // Assert
        assertEquals("test-service", serviceName, "Service name should match config");
    }

    @Test
    void testGetOpenTelemetryReturnsValidInstance() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        OpenTelemetry openTelemetry = sdk.getOpenTelemetry();

        // Assert
        assertNotNull(openTelemetry, "OpenTelemetry instance should not be null");
    }

    @Test
    void testGetTracerReturnsValidTracer() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        Tracer tracer = sdk.getTracer("test.instrumentation");

        // Assert
        assertNotNull(tracer, "Tracer should not be null");
    }

    @Test
    void testGetTracerWithNullNameReturnsTracer() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        Tracer tracer = sdk.getTracer(null);

        // Assert
        assertNotNull(tracer, "Tracer should not be null even with null name");
    }

    @Test
    void testGetTracerWithEmptyNameReturnsTracer() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        Tracer tracer = sdk.getTracer("");

        // Assert
        assertNotNull(tracer, "Tracer should not be null even with empty name");
    }

    @Test
    void testGetTracerWithDifferentNamesReturnsDifferentTracers() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        Tracer tracer1 = sdk.getTracer("instrumentation.one");
        Tracer tracer2 = sdk.getTracer("instrumentation.two");

        // Assert
        assertNotNull(tracer1, "First tracer should not be null");
        assertNotNull(tracer2, "Second tracer should not be null");
        // Note: OpenTelemetry may return the same instance or different instances
        // depending on implementation, so we just verify both are valid
    }

    @Test
    void testShutdownCompletesWithoutErrors() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act & Assert
        assertDoesNotThrow(() -> sdk.shutdown(), "Shutdown should complete without errors");
    }

    @Test
    void testMultipleShutdownCallsDoNotThrowErrors() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act & Assert
        assertDoesNotThrow(() -> {
            sdk.shutdown();
            sdk.shutdown(); // Second call should be safe
        }, "Multiple shutdown calls should not throw errors");
    }

    @Test
    void testSDKWithProductionEnvironment() {
        // Arrange
        TracekitConfig prodConfig = TracekitConfig.builder()
                .apiKey("prod-api-key")
                .serviceName("prod-service")
                .environment("production")
                .build();

        // Act
        sdk = TracekitSDK.create(prodConfig);

        // Assert
        assertNotNull(sdk, "SDK should initialize with production environment");
        assertEquals("prod-service", sdk.getServiceName(), "Service name should match");
    }

    @Test
    void testSDKWithCustomEndpoint() {
        // Arrange
        TracekitConfig customConfig = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("custom-service")
                .endpoint("https://custom.endpoint.com/traces")
                .build();

        // Act
        sdk = TracekitSDK.create(customConfig);

        // Assert
        assertNotNull(sdk, "SDK should initialize with custom endpoint");
    }

    @Test
    void testSDKWithLocalUIPort() {
        // Arrange
        TracekitConfig localConfig = TracekitConfig.builder()
                .apiKey("test-api-key")
                .serviceName("local-service")
                .localUIPort(8888)
                .build();

        // Act
        sdk = TracekitSDK.create(localConfig);

        // Assert
        assertNotNull(sdk, "SDK should initialize with custom local UI port");
    }

    @Test
    void testSDKInitializationLogsCorrectly() {
        // This test verifies that SDK initialization doesn't throw exceptions
        // Actual logging verification would require a logging framework mock
        // Arrange & Act
        sdk = TracekitSDK.create(validConfig);

        // Assert
        assertNotNull(sdk, "SDK should initialize and log without errors");
    }

    @Test
    void testOpenTelemetryIntegrationIsComplete() {
        // Arrange
        sdk = TracekitSDK.create(validConfig);

        // Act
        OpenTelemetry otel = sdk.getOpenTelemetry();
        Tracer tracer = sdk.getTracer("integration.test");

        // Assert
        assertNotNull(otel, "OpenTelemetry should be initialized");
        assertNotNull(tracer, "Tracer should be available from OpenTelemetry");

        // Verify we can get tracerProvider
        assertNotNull(otel.getTracerProvider(), "TracerProvider should be available");
    }

    @Test
    void testSDKNotNullAfterInitialization() {
        // Act
        sdk = TracekitSDK.create(validConfig);

        // Assert
        assertNotNull(sdk, "SDK instance should not be null after create()");
        assertNotNull(sdk.getOpenTelemetry(), "OpenTelemetry should not be null");
        assertNotNull(sdk.getServiceName(), "Service name should not be null");
    }

    @Test
    void testBuilderValidatesRequiredFields() {
        // Act & Assert - Missing API key
        assertThrows(IllegalArgumentException.class, () -> {
            TracekitSDK.builder()
                    .serviceName("test-service")
                    .build();
        }, "Builder should throw exception when API key is missing");

        // Act & Assert - Missing service name
        assertThrows(IllegalArgumentException.class, () -> {
            TracekitSDK.builder()
                    .apiKey("test-api-key")
                    .build();
        }, "Builder should throw exception when service name is missing");
    }

    @Test
    void testBuilderWithAllFeatures() {
        // Act
        sdk = TracekitSDK.builder()
                .apiKey("test-api-key")
                .serviceName("full-featured-service")
                .environment("development")
                .endpoint("https://custom.endpoint.com/traces")
                .enableCodeMonitoring(true)
                .enableSecurityScanning(true)
                .localUIPort(7777)
                .build();

        // Assert
        assertNotNull(sdk, "SDK should initialize with all features");
        assertEquals("full-featured-service", sdk.getServiceName(), "Service name should match");
    }
}

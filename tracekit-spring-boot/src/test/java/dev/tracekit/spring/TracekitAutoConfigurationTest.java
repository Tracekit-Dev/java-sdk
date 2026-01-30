package dev.tracekit.spring;

import dev.tracekit.TracekitSDK;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Tracekit Spring Boot auto-configuration.
 *
 * <p>These tests verify that the auto-configuration correctly:</p>
 * <ul>
 *   <li>Creates TracekitSDK bean when enabled</li>
 *   <li>Binds configuration properties correctly</li>
 *   <li>Respects conditional loading (enabled=false)</li>
 *   <li>Provides functional OpenTelemetry integration</li>
 * </ul>
 */
class TracekitAutoConfigurationTest {

    /**
     * Test that auto-configuration loads TracekitSDK bean with valid configuration.
     */
    @Nested
    @SpringBootTest(classes = TracekitAutoConfigurationTest.TestApplication.class)
    @TestPropertySource(properties = {
            "tracekit.enabled=true",
            "tracekit.api-key=test-api-key",
            "tracekit.service-name=test-service",
            "tracekit.environment=test",
            "tracekit.endpoint=https://test.tracekit.dev/v1/traces"
    })
    class AutoConfigurationLoadedTest {

        @Autowired
        private ApplicationContext context;

        @Autowired(required = false)
        private TracekitSDK tracekitSDK;

        @Test
        void shouldLoadTracekitSDKBean() {
            assertThat(tracekitSDK).isNotNull();
            assertThat(context.getBean(TracekitSDK.class)).isNotNull();
        }

        @Test
        void shouldProvideOpenTelemetryInstance() {
            assertThat(tracekitSDK).isNotNull();
            OpenTelemetry openTelemetry = tracekitSDK.getOpenTelemetry();
            assertThat(openTelemetry).isNotNull();
        }

        @Test
        void shouldProvideTracer() {
            assertThat(tracekitSDK).isNotNull();
            Tracer tracer = tracekitSDK.getTracer("test-instrumentation");
            assertThat(tracer).isNotNull();
        }

        @Test
        void shouldHaveCorrectServiceName() {
            assertThat(tracekitSDK).isNotNull();
            assertThat(tracekitSDK.getServiceName()).isEqualTo("test-service");
        }
    }

    /**
     * Test that configuration properties are correctly bound.
     */
    @Nested
    @SpringBootTest(classes = TracekitAutoConfigurationTest.TestApplication.class)
    @TestPropertySource(properties = {
            "tracekit.enabled=true",
            "tracekit.api-key=test-api-key-123",
            "tracekit.service-name=my-test-service",
            "tracekit.environment=staging",
            "tracekit.endpoint=https://custom.tracekit.dev/v1/traces",
            "tracekit.enable-code-monitoring=true",
            "tracekit.enable-security-scanning=true",
            "tracekit.local-ui-port=8888"
    })
    class ConfigurationPropertiesBindingTest {

        @Autowired
        private TracekitProperties properties;

        @Test
        void shouldBindAllProperties() {
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getApiKey()).isEqualTo("test-api-key-123");
            assertThat(properties.getServiceName()).isEqualTo("my-test-service");
            assertThat(properties.getEnvironment()).isEqualTo("staging");
            assertThat(properties.getEndpoint()).isEqualTo("https://custom.tracekit.dev/v1/traces");
            assertThat(properties.isEnableCodeMonitoring()).isTrue();
            assertThat(properties.isEnableSecurityScanning()).isTrue();
            assertThat(properties.getLocalUiPort()).isEqualTo(8888);
        }
    }

    /**
     * Test that auto-configuration is disabled when enabled=false.
     */
    @Nested
    @SpringBootTest(classes = TracekitAutoConfigurationTest.TestApplication.class)
    @TestPropertySource(properties = {
            "tracekit.enabled=false",
            "tracekit.api-key=test-api-key",
            "tracekit.service-name=test-service"
    })
    class ConditionalLoadingDisabledTest {

        @Autowired
        private ApplicationContext context;

        @Test
        void shouldNotLoadTracekitSDKBeanWhenDisabled() {
            assertThat(context.containsBean("tracekitSDK")).isFalse();
        }
    }

    /**
     * Test that auto-configuration uses default values when not specified.
     */
    @Nested
    @SpringBootTest(classes = TracekitAutoConfigurationTest.TestApplication.class)
    @TestPropertySource(properties = {
            "tracekit.api-key=test-api-key",
            "tracekit.service-name=test-service"
    })
    class DefaultValuesTest {

        @Autowired
        private TracekitProperties properties;

        @Autowired(required = false)
        private TracekitSDK tracekitSDK;

        @Test
        void shouldUseDefaultValues() {
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getEndpoint()).isEqualTo("https://app.tracekit.dev/v1/traces");
            assertThat(properties.getEnvironment()).isEqualTo("production");
            assertThat(properties.isEnableCodeMonitoring()).isFalse();
            assertThat(properties.isEnableSecurityScanning()).isFalse();
            assertThat(properties.getLocalUiPort()).isEqualTo(9999);
        }

        @Test
        void shouldLoadWithDefaultValues() {
            assertThat(tracekitSDK).isNotNull();
        }
    }

    /**
     * Minimal Spring Boot application for testing.
     */
    @SpringBootApplication
    static class TestApplication {
        // Empty test application
    }
}

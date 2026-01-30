package dev.tracekit;

import dev.tracekit.local.LocalUIDetector;
import dev.tracekit.snapshot.SnapshotClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Main SDK class for Tracekit Java SDK with OpenTelemetry integration.
 *
 * <p>This class provides the primary entry point for initializing and using
 * the Tracekit SDK. It integrates OpenTelemetry for distributed tracing,
 * automatically detects local UI, and manages the lifecycle of tracing components.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Using factory method
 * TracekitConfig config = TracekitConfig.builder()
 *     .apiKey("your-api-key")
 *     .serviceName("my-service")
 *     .environment("production")
 *     .build();
 *
 * TracekitSDK sdk = TracekitSDK.create(config);
 *
 * // Using builder pattern
 * TracekitSDK sdk = TracekitSDK.builder()
 *     .apiKey("your-api-key")
 *     .serviceName("my-service")
 *     .environment("production")
 *     .build();
 *
 * // Get OpenTelemetry instance
 * OpenTelemetry openTelemetry = sdk.getOpenTelemetry();
 *
 * // Get tracer for instrumentation
 * Tracer tracer = sdk.getTracer("my.instrumentation");
 *
 * // Shutdown when done
 * sdk.shutdown();
 * </pre>
 *
 * <p>Thread-safe implementation with proper resource management.</p>
 */
public final class TracekitSDK {

    private static final Logger logger = LoggerFactory.getLogger(TracekitSDK.class);
    private static final String SDK_VERSION = "1.0.0"; // TODO: Load from build metadata

    private final TracekitConfig config;
    private final TracekitClient client;
    private final OpenTelemetry openTelemetry;
    private final SdkTracerProvider tracerProvider;
    private final SnapshotClient snapshotClient;
    private volatile boolean isShutdown = false;

    /**
     * Creates a new TracekitSDK instance with the given configuration.
     *
     * <p>This constructor performs the following initialization steps:</p>
     * <ul>
     *   <li>Auto-detects local UI using {@link LocalUIDetector}</li>
     *   <li>Initializes {@link TracekitClient} with cloud and optional local endpoints</li>
     *   <li>Sets up OpenTelemetry SDK with OTLP HTTP exporter</li>
     *   <li>Configures resource attributes (service.name, deployment.environment)</li>
     *   <li>Registers as global OpenTelemetry instance</li>
     * </ul>
     *
     * @param config the Tracekit configuration (must not be null)
     * @param registerGlobal whether to register as global OpenTelemetry instance
     * @throws NullPointerException if config is null
     */
    private TracekitSDK(TracekitConfig config, boolean registerGlobal) {
        this.config = Objects.requireNonNull(config, "config must not be null");

        logger.info("Initializing Tracekit SDK v{} for service: {}, environment: {}",
                SDK_VERSION, config.getServiceName(), config.getEnvironment());

        // Auto-detect local UI
        LocalUIDetector localUIDetector = new LocalUIDetector(config.getLocalUIPort());
        String localEndpoint = localUIDetector.getLocalUIEndpoint();

        // Initialize TracekitClient with cloud and optional local endpoints
        this.client = new TracekitClient(config, localEndpoint);

        // Set up OpenTelemetry SDK
        this.tracerProvider = createTracerProvider(config);
        this.openTelemetry = buildOpenTelemetry(tracerProvider, registerGlobal);

        // Initialize SnapshotClient if code monitoring is enabled
        if (config.isEnableCodeMonitoring()) {
            this.snapshotClient = new SnapshotClient(
                config.getApiKey(),
                config.getEndpoint().replace("/v1/traces", ""),
                config.getServiceName()
            );
            this.snapshotClient.start();
            logger.info("Code monitoring enabled - Snapshot client started");
        } else {
            this.snapshotClient = null;
        }

        logger.info("Tracekit SDK initialized successfully. Cloud endpoint: {}, Local endpoint: {}",
                config.getEndpoint(), localEndpoint != null ? localEndpoint : "none");
    }

    /**
     * Builds the OpenTelemetry instance.
     *
     * @param tracerProvider the tracer provider to use
     * @param registerGlobal whether to register as global instance
     * @return the OpenTelemetry instance
     */
    private OpenTelemetry buildOpenTelemetry(SdkTracerProvider tracerProvider, boolean registerGlobal) {
        if (registerGlobal) {
            try {
                return OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerProvider)
                        .buildAndRegisterGlobal();
            } catch (IllegalStateException e) {
                // Global already registered, build without global registration
                logger.debug("Global OpenTelemetry already registered, using non-global instance");
                return OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerProvider)
                        .build();
            }
        } else {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
        }
    }

    /**
     * Creates the OpenTelemetry tracer provider with OTLP HTTP exporter.
     *
     * @param config the Tracekit configuration
     * @return the configured SdkTracerProvider
     */
    private SdkTracerProvider createTracerProvider(TracekitConfig config) {
        // Create resource with service.name and deployment.environment attributes
        Resource resource = Resource.getDefault().merge(
                Resource.create(
                        Attributes.builder()
                                .put(ResourceAttributes.SERVICE_NAME, config.getServiceName())
                                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, config.getEnvironment())
                                .build()
                )
        );

        // Create OTLP HTTP span exporter with API key header
        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(config.getEndpoint())
                .addHeader("X-API-Key", config.getApiKey())
                .addHeader("User-Agent", "tracekit-java-sdk/" + SDK_VERSION)
                .build();

        // Create tracer provider with batch span processor
        return SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(1, TimeUnit.SECONDS) // Export every second
                        .setMaxQueueSize(2048)
                        .setMaxExportBatchSize(512)
                        .build())
                .build();
    }

    /**
     * Static factory method to create a TracekitSDK instance.
     *
     * @param config the Tracekit configuration (must not be null)
     * @return a new TracekitSDK instance
     * @throws NullPointerException if config is null
     */
    public static TracekitSDK create(TracekitConfig config) {
        return new TracekitSDK(config, true);
    }

    /**
     * Creates a new builder for constructing TracekitSDK instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the OpenTelemetry instance for this SDK.
     *
     * <p>This instance can be used to get tracers, meters, and other
     * OpenTelemetry components.</p>
     *
     * @return the OpenTelemetry instance
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Gets a tracer for the specified instrumentation name.
     *
     * <p>If the instrumentation name is null or empty, a default tracer
     * will be returned.</p>
     *
     * @param instrumentationName the name of the instrumentation library
     * @return a Tracer instance
     */
    public Tracer getTracer(String instrumentationName) {
        if (instrumentationName == null || instrumentationName.trim().isEmpty()) {
            instrumentationName = config.getServiceName();
        }
        return openTelemetry.getTracer(instrumentationName);
    }

    /**
     * Gets the service name from the configuration.
     *
     * @return the service name
     */
    public String getServiceName() {
        return config.getServiceName();
    }

    /**
     * Captures a snapshot of local variables at the current code location.
     * This is only active if code monitoring is enabled and there's an active breakpoint.
     *
     * @param label a unique label identifying this capture point
     * @param variables map of variable names to values to capture
     */
    public void captureSnapshot(String label, Map<String, Object> variables) {
        if (snapshotClient != null) {
            snapshotClient.checkAndCaptureWithContext(label, variables);
        }
    }

    /**
     * Shuts down the SDK and releases all resources.
     *
     * <p>This method is idempotent and can be called multiple times safely.
     * It will close the tracer provider and flush any pending spans.</p>
     *
     * <p>After shutdown, the SDK should not be used for creating new spans.</p>
     */
    public void shutdown() {
        if (isShutdown) {
            logger.debug("SDK already shut down, skipping shutdown");
            return;
        }

        synchronized (this) {
            if (isShutdown) {
                return;
            }

            logger.info("Shutting down Tracekit SDK for service: {}", config.getServiceName());

            try {
                // Stop snapshot client if running
                if (snapshotClient != null) {
                    snapshotClient.stop();
                }

                // Close the tracer provider (this will flush pending spans)
                tracerProvider.close();
                isShutdown = true;
                logger.info("Tracekit SDK shut down successfully");
            } catch (Exception e) {
                logger.error("Error during SDK shutdown", e);
            }
        }
    }

    /**
     * Builder class for constructing TracekitSDK instances.
     *
     * <p>This builder delegates to {@link TracekitConfig.Builder} for configuration
     * and provides a fluent API for SDK initialization.</p>
     */
    public static final class Builder {

        private final TracekitConfig.Builder configBuilder;

        private Builder() {
            this.configBuilder = TracekitConfig.builder();
        }

        /**
         * Sets the API key for authentication.
         *
         * @param apiKey the API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            configBuilder.apiKey(apiKey);
            return this;
        }

        /**
         * Sets the service name.
         *
         * @param serviceName the service name
         * @return this builder
         */
        public Builder serviceName(String serviceName) {
            configBuilder.serviceName(serviceName);
            return this;
        }

        /**
         * Sets the endpoint URL for sending traces.
         *
         * @param endpoint the endpoint URL
         * @return this builder
         */
        public Builder endpoint(String endpoint) {
            configBuilder.endpoint(endpoint);
            return this;
        }

        /**
         * Sets the deployment environment.
         *
         * @param environment the environment name (e.g., "production", "staging")
         * @return this builder
         */
        public Builder environment(String environment) {
            configBuilder.environment(environment);
            return this;
        }

        /**
         * Enables or disables code monitoring.
         *
         * @param enableCodeMonitoring true to enable code monitoring
         * @return this builder
         */
        public Builder enableCodeMonitoring(boolean enableCodeMonitoring) {
            configBuilder.enableCodeMonitoring(enableCodeMonitoring);
            return this;
        }

        /**
         * Enables or disables security scanning.
         *
         * @param enableSecurityScanning true to enable security scanning
         * @return this builder
         */
        public Builder enableSecurityScanning(boolean enableSecurityScanning) {
            configBuilder.enableSecurityScanning(enableSecurityScanning);
            return this;
        }

        /**
         * Sets the local UI port for auto-detection.
         *
         * @param localUIPort the port number (default: 9999)
         * @return this builder
         */
        public Builder localUIPort(int localUIPort) {
            configBuilder.localUIPort(localUIPort);
            return this;
        }

        /**
         * Builds and initializes the TracekitSDK instance.
         *
         * @return a new TracekitSDK instance
         * @throws IllegalArgumentException if required configuration is missing
         */
        public TracekitSDK build() {
            TracekitConfig config = configBuilder.build();
            return new TracekitSDK(config, true);
        }
    }
}

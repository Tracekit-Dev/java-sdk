package dev.tracekit.spring;

import dev.tracekit.TracekitConfig;
import dev.tracekit.TracekitSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PreDestroy;

/**
 * Auto-configuration for Tracekit Spring Boot integration.
 *
 * <p>This auto-configuration is activated when:</p>
 * <ul>
 *   <li>{@link TracekitSDK} is on the classpath</li>
 *   <li>The property {@code tracekit.enabled} is true (default) or not set</li>
 *   <li>No existing {@link TracekitSDK} bean is already defined</li>
 * </ul>
 *
 * <p>The auto-configuration creates a {@link TracekitSDK} bean configured
 * from {@link TracekitProperties} and manages its lifecycle automatically.</p>
 *
 * <p>To disable auto-configuration, set {@code tracekit.enabled=false} in
 * application.properties or application.yml.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @Service
 * public class MyService {
 *     private final TracekitSDK tracekitSDK;
 *
 *     @Autowired
 *     public MyService(TracekitSDK tracekitSDK) {
 *         this.tracekitSDK = tracekitSDK;
 *     }
 *
 *     public void doSomething() {
 *         Tracer tracer = tracekitSDK.getTracer("my-instrumentation");
 *         // Use tracer for manual instrumentation
 *     }
 * }
 * }
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(TracekitSDK.class)
@ConditionalOnProperty(prefix = "tracekit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TracekitProperties.class)
public class TracekitAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TracekitAutoConfiguration.class);

    private TracekitSDK tracekitSDK;

    /**
     * Creates and configures the TracekitSDK bean.
     *
     * <p>This method initializes the SDK with configuration properties from
     * {@link TracekitProperties}. It validates that required properties
     * (apiKey and serviceName) are set.</p>
     *
     * @param properties the Tracekit configuration properties
     * @return the configured TracekitSDK instance
     * @throws IllegalArgumentException if required properties are missing
     */
    @Bean
    @ConditionalOnMissingBean
    public TracekitSDK tracekitSDK(TracekitProperties properties) {
        logger.info("Initializing Tracekit SDK with Spring Boot auto-configuration");

        // Validate required properties
        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tracekit API key is required. Please set 'tracekit.api-key' in application properties.");
        }

        if (properties.getServiceName() == null || properties.getServiceName().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tracekit service name is required. Please set 'tracekit.service-name' in application properties.");
        }

        // Build TracekitConfig from properties
        TracekitConfig config = TracekitConfig.builder()
                .apiKey(properties.getApiKey())
                .serviceName(properties.getServiceName())
                .endpoint(properties.getEndpoint())
                .environment(properties.getEnvironment())
                .enableCodeMonitoring(properties.isEnableCodeMonitoring())
                .enableSecurityScanning(properties.isEnableSecurityScanning())
                .localUIPort(properties.getLocalUiPort())
                .build();

        // Create and store SDK instance for shutdown
        this.tracekitSDK = TracekitSDK.create(config);

        logger.info("Tracekit SDK initialized successfully for service: {}, environment: {}",
                properties.getServiceName(), properties.getEnvironment());

        return this.tracekitSDK;
    }

    /**
     * Shuts down the TracekitSDK when the application context is closed.
     *
     * <p>This ensures that all pending spans are flushed and resources
     * are properly released when the Spring Boot application shuts down.</p>
     */
    @PreDestroy
    public void shutdown() {
        if (tracekitSDK != null) {
            logger.info("Shutting down Tracekit SDK");
            tracekitSDK.shutdown();
        }
    }
}

package dev.tracekit.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Tracekit Spring Boot integration.
 *
 * <p>These properties can be configured in application.properties or application.yml
 * with the prefix "tracekit".</p>
 *
 * <p>Example configuration in application.properties:</p>
 * <pre>
 * tracekit.enabled=true
 * tracekit.api-key=your-api-key
 * tracekit.service-name=my-service
 * tracekit.environment=production
 * tracekit.endpoint=https://app.tracekit.dev/v1/traces
 * tracekit.enable-code-monitoring=false
 * tracekit.enable-security-scanning=false
 * tracekit.local-ui-port=9999
 * </pre>
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>
 * tracekit:
 *   enabled: true
 *   api-key: your-api-key
 *   service-name: my-service
 *   environment: production
 *   endpoint: https://app.tracekit.dev/v1/traces
 *   enable-code-monitoring: false
 *   enable-security-scanning: false
 *   local-ui-port: 9999
 * </pre>
 */
@ConfigurationProperties(prefix = "tracekit")
public class TracekitProperties {

    /**
     * Enable or disable Tracekit auto-configuration.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * API key for authentication with Tracekit backend.
     * Required when enabled is true.
     */
    private String apiKey;

    /**
     * Name of the service being traced.
     * Required when enabled is true.
     */
    private String serviceName;

    /**
     * Endpoint URL for sending traces.
     * Default: https://app.tracekit.dev/v1/traces
     */
    private String endpoint = "https://app.tracekit.dev/v1/traces";

    /**
     * Deployment environment name (e.g., "production", "staging", "development").
     * Default: production
     */
    private String environment = "production";

    /**
     * Enable code monitoring features.
     * Default: false
     */
    private boolean enableCodeMonitoring = false;

    /**
     * Enable security scanning features.
     * Default: false
     */
    private boolean enableSecurityScanning = false;

    /**
     * Port number for local UI auto-detection.
     * Default: 9999
     */
    private int localUiPort = 9999;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isEnableCodeMonitoring() {
        return enableCodeMonitoring;
    }

    public void setEnableCodeMonitoring(boolean enableCodeMonitoring) {
        this.enableCodeMonitoring = enableCodeMonitoring;
    }

    public boolean isEnableSecurityScanning() {
        return enableSecurityScanning;
    }

    public void setEnableSecurityScanning(boolean enableSecurityScanning) {
        this.enableSecurityScanning = enableSecurityScanning;
    }

    public int getLocalUiPort() {
        return localUiPort;
    }

    public void setLocalUiPort(int localUiPort) {
        this.localUiPort = localUiPort;
    }
}

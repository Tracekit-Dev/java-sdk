package dev.tracekit;

public final class TracekitConfig {

    private final String apiKey;
    private final String serviceName;
    private final String endpoint;
    private final String environment;
    private final boolean enableCodeMonitoring;
    private final boolean enableSecurityScanning;
    private final int localUIPort;

    private TracekitConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.serviceName = builder.serviceName;
        this.endpoint = builder.endpoint;
        this.environment = builder.environment;
        this.enableCodeMonitoring = builder.enableCodeMonitoring;
        this.enableSecurityScanning = builder.enableSecurityScanning;
        this.localUIPort = builder.localUIPort;

        validate();
    }

    private void validate() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey is required and cannot be null or empty");
        }
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("serviceName is required and cannot be null or empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isEnableCodeMonitoring() {
        return enableCodeMonitoring;
    }

    public boolean isEnableSecurityScanning() {
        return enableSecurityScanning;
    }

    public int getLocalUIPort() {
        return localUIPort;
    }

    public static final class Builder {
        private String apiKey;
        private String serviceName;
        private String endpoint = "https://app.tracekit.dev/v1/traces";
        private String environment = "production";
        private boolean enableCodeMonitoring = false;
        private boolean enableSecurityScanning = false;
        private int localUIPort = 9999;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder environment(String environment) {
            if (environment == null || environment.trim().isEmpty()) {
                this.environment = "production";
            } else {
                this.environment = environment;
            }
            return this;
        }

        public Builder enableCodeMonitoring(boolean enableCodeMonitoring) {
            this.enableCodeMonitoring = enableCodeMonitoring;
            return this;
        }

        public Builder enableSecurityScanning(boolean enableSecurityScanning) {
            this.enableSecurityScanning = enableSecurityScanning;
            return this;
        }

        public Builder localUIPort(int localUIPort) {
            this.localUIPort = localUIPort;
            return this;
        }

        public TracekitConfig build() {
            return new TracekitConfig(this);
        }
    }
}

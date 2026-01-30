# TraceKit Java SDK

Official Java/Kotlin SDK for TraceKit APM - OpenTelemetry-based distributed tracing and application performance monitoring for JVM applications.

## Overview

TraceKit Java SDK provides production-ready distributed tracing capabilities for Java and Kotlin applications. Built on OpenTelemetry standards, it offers seamless integration with popular frameworks, automatic local development support, and comprehensive security scanning.

## Features

- **OpenTelemetry-Native**: Built on OpenTelemetry 1.32.0 for maximum compatibility and standardization
- **Distributed Tracing**: Full support for distributed trace propagation across microservices
- **Security Scanning**: Automatic detection of sensitive data (PII, credentials, API keys) in traces
- **Local UI Auto-Detection**: Automatically sends traces to local TraceKit UI when running in development
- **Spring Boot Integration**: Zero-configuration auto-instrumentation via Spring Boot starter
- **Framework Support**: HTTP requests, REST controllers, and JVM metrics automatically captured
- **Flexible Configuration**: Environment variables, properties files, or programmatic configuration
- **Production-Ready**: Comprehensive error handling, resource management, and graceful shutdown

## Installation

### Maven

For Spring Boot applications:

```xml
<dependency>
    <groupId>dev.tracekit</groupId>
    <artifactId>tracekit-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For vanilla Java applications:

```xml
<dependency>
    <groupId>dev.tracekit</groupId>
    <artifactId>tracekit-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

For Spring Boot applications:

```gradle
implementation 'dev.tracekit:tracekit-spring-boot-starter:1.0.0-SNAPSHOT'
```

For vanilla Java applications:

```gradle
implementation 'dev.tracekit:tracekit-core:1.0.0-SNAPSHOT'
```

## Quick Start

### Spring Boot (Recommended)

1. Add the Spring Boot starter dependency (see Installation above)

2. Configure in `application.yml`:

```yaml
tracekit:
  enabled: true
  api-key: ${TRACEKIT_API_KEY}
  service-name: my-service
  environment: production
  endpoint: https://api.tracekit.dev/v1/traces
  enable-security-scanning: true
```

Or in `application.properties`:

```properties
tracekit.enabled=true
tracekit.api-key=${TRACEKIT_API_KEY}
tracekit.service-name=my-service
tracekit.environment=production
tracekit.endpoint=https://api.tracekit.dev/v1/traces
tracekit.enable-security-scanning=true
```

3. That's it! The SDK auto-configures and starts tracing automatically.

### Vanilla Java

```java
import dev.tracekit.TracekitSDK;
import dev.tracekit.TracekitConfig;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;

public class Application {
    public static void main(String[] args) {
        // Initialize SDK
        TracekitConfig config = TracekitConfig.builder()
            .apiKey(System.getenv("TRACEKIT_API_KEY"))
            .serviceName("my-service")
            .environment("production")
            .enableSecurityScanning(true)
            .build();

        TracekitSDK sdk = TracekitSDK.create(config);

        // Get tracer
        Tracer tracer = sdk.getTracer("my.application");

        // Create spans
        Span span = tracer.spanBuilder("process-request").startSpan();
        try {
            span.setAttribute("user.id", "12345");
            // Your business logic here
        } finally {
            span.end();
        }

        // Shutdown on application exit
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::shutdown));
    }
}
```

### Kotlin DSL (Coming Soon)

```kotlin
// Future API - not yet implemented
TraceKit.configure {
    apiKey = System.getenv("TRACEKIT_API_KEY")
    serviceName = "my-service"
    environment = "production"
    enableSecurityScanning = true
}
```

## Modules

| Module | Description | Status |
|--------|-------------|--------|
| `tracekit-core` | Core SDK with OpenTelemetry integration, security scanning, and local UI detection | Implemented |
| `tracekit-spring-boot-starter` | Spring Boot auto-configuration and starter | Implemented |
| `tracekit-kotlin` | Kotlin extensions and DSL support | Planned |
| `tracekit-micronaut` | Micronaut framework integration | Planned |
| `tracekit-quarkus` | Quarkus framework integration | Planned |

## Framework Support

| Framework | Version | Support Level | Auto-Instrumentation |
|-----------|---------|---------------|---------------------|
| Spring Boot | 3.2.1+ | Full | Yes (via starter) |
| Spring Framework | 6.0+ | Partial | Manual setup required |
| Micronaut | 4.2+ | Planned | Coming soon |
| Quarkus | 3.6+ | Planned | Coming soon |
| Vanilla Java | 11+ | Full | Manual instrumentation |

## Configuration Reference

### Spring Boot Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracekit.enabled` | boolean | `true` | Enable/disable TraceKit |
| `tracekit.api-key` | string | - | API key for authentication (required) |
| `tracekit.service-name` | string | - | Service name in traces (required) |
| `tracekit.environment` | string | `production` | Deployment environment |
| `tracekit.endpoint` | string | `https://app.tracekit.dev/v1/traces` | Cloud endpoint URL |
| `tracekit.enable-code-monitoring` | boolean | `false` | Enable code monitoring features |
| `tracekit.enable-security-scanning` | boolean | `false` | Enable security scanning |
| `tracekit.local-ui-port` | int | `9999` | Port for local UI auto-detection |

### Programmatic Configuration

```java
TracekitConfig config = TracekitConfig.builder()
    .apiKey("your-api-key")
    .serviceName("my-service")
    .environment("production")
    .endpoint("https://api.tracekit.dev/v1/traces")
    .enableCodeMonitoring(false)
    .enableSecurityScanning(true)
    .localUIPort(9999)
    .build();
```

## Security Scanning

TraceKit automatically scans trace data for sensitive information when `enable-security-scanning` is enabled:

- **PII Detection**: Email addresses, phone numbers, SSNs, credit cards
- **Credential Detection**: API keys, passwords, tokens, private keys
- **Custom Patterns**: Extend with your own sensitive data patterns

Detected sensitive data is flagged but not automatically redacted, allowing you to review and configure appropriate handling.

## Local Development

The SDK automatically detects when TraceKit Local UI is running (default port 9999) and sends traces to both:
- TraceKit Cloud (production endpoint)
- Local UI (for real-time development feedback)

No configuration needed - just start the Local UI and the SDK will detect it.

## Examples

See the [examples](examples/) directory for complete working examples:

- **[spring-boot-example](examples/spring-boot-example/)**: Spring Boot REST API with auto-instrumentation

Each example includes:
- Complete source code
- Configuration files
- README with setup instructions
- Build and run commands

## Requirements

- **Java**: 11 or higher
- **Build Tools**: Maven 3.6+ or Gradle 7.0+
- **Dependencies**: Managed via BOM (Bill of Materials)

## Documentation

- [Architecture Overview](docs/architecture.md) - Coming soon
- [Spring Boot Integration](docs/spring-boot.md) - Coming soon
- [Security Scanning Guide](docs/security.md) - Coming soon
- [API Documentation](https://docs.tracekit.dev/java-sdk) - Coming soon

## Building from Source

```bash
# Clone the repository
git clone https://github.com/context-io/tracekit-java-sdk.git
cd tracekit-java-sdk

# Build all modules
mvn clean install

# Run tests
mvn test

# Build without tests
mvn clean install -DskipTests
```

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for:
- How to build and test
- Code style guidelines
- Pull request process
- Development workflow

## Support

- **Documentation**: https://docs.tracekit.dev
- **Issues**: https://github.com/context-io/tracekit-java-sdk/issues
- **Email**: support@tracekit.dev

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

Built on [OpenTelemetry](https://opentelemetry.io/) - the industry standard for observability.

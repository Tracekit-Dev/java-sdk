# TraceKit Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/dev.tracekit/tracekit-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.tracekit/tracekit-core)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-11+-blue.svg)](https://www.oracle.com/java/)

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
- **Client IP Capture**: Automatic IP detection for DDoS & traffic analysis
- **Flexible Configuration**: Environment variables, properties files, or programmatic configuration
- **Production-Ready**: Comprehensive error handling, resource management, and graceful shutdown

## Installation

### Maven

For Spring Boot applications:

```xml
<dependency>
    <groupId>dev.tracekit</groupId>
    <artifactId>tracekit-spring-boot-starter</artifactId>
    <version>1.0.0</version>
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
  enable-code-monitoring: true  # Enables snapshot capture with automatic security scanning
```

Or in `application.properties`:

```properties
tracekit.enabled=true
tracekit.api-key=${TRACEKIT_API_KEY}
tracekit.service-name=my-service
tracekit.environment=production
tracekit.endpoint=https://api.tracekit.dev/v1/traces
tracekit.enable-code-monitoring=true
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
            .enableCodeMonitoring(true)  // Enables snapshot capture with automatic security scanning
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

### Kotlin Usage

Kotlin developers can use the Java API directly (100% Kotlin-compatible):

```kotlin
import dev.tracekit.TracekitSDK
import dev.tracekit.TracekitConfig

fun main() {
    // Initialize SDK using Java API (works perfectly in Kotlin)
    val config = TracekitConfig.builder()
        .apiKey(System.getenv("TRACEKIT_API_KEY"))
        .serviceName("my-service")
        .environment("production")
        .enableCodeMonitoring(true)
        .build()

    val sdk = TracekitSDK.create(config)

    // Your application code here...

    // Shutdown on application exit
    Runtime.getRuntime().addShutdownHook(Thread { sdk.shutdown() })
}
```

**Note**: A Kotlin-specific DSL with idiomatic extensions is planned for a future release.

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
| Spring Boot | 3.2.1+ | ✅ Full | Yes (via starter) |
| Spring Framework | 6.0+ | ⚠️ Partial | Manual setup required |
| Vanilla Java | 11+ | ✅ Full | Manual instrumentation |

**Coming Soon**: Micronaut and Quarkus framework integrations are planned for future releases.

## Configuration Reference

### Spring Boot Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracekit.enabled` | boolean | `true` | Enable/disable TraceKit |
| `tracekit.api-key` | string | - | API key for authentication (required) |
| `tracekit.service-name` | string | - | Service name in traces (required) |
| `tracekit.environment` | string | `production` | Deployment environment |
| `tracekit.endpoint` | string | `https://app.tracekit.dev/v1/traces` | Cloud endpoint URL |
| `tracekit.enable-code-monitoring` | boolean | `true` | Enable snapshot capture (includes automatic security scanning) |
| `tracekit.local-ui-port` | int | `9999` | Port for local UI auto-detection |

### Programmatic Configuration

```java
TracekitConfig config = TracekitConfig.builder()
    .apiKey("your-api-key")
    .serviceName("my-service")
    .environment("production")
    .endpoint("https://api.tracekit.dev/v1/traces")
    .enableCodeMonitoring(true)  // Enables snapshot capture with automatic security scanning
    .localUIPort(9999)
    .build();
```

## Security Scanning

TraceKit automatically scans captured snapshots for sensitive information when code monitoring is enabled (`enable-code-monitoring: true`):

- **PII Detection**: Email addresses, phone numbers, SSNs, credit cards
- **Credential Detection**: API keys (AWS, Stripe, OpenAI, etc.), passwords, tokens, private keys
- **Automatic Redaction**: Sensitive values are replaced with `[REDACTED]` before sending to the backend
- **Security Flags**: Each detection creates a security flag with severity level (CRITICAL, HIGH, MEDIUM, LOW)

Security scanning happens automatically for all `captureSnapshot()` calls - no additional configuration needed.

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

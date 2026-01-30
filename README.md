# TraceKit Java SDK

Official Java/Kotlin SDK for TraceKit APM - OpenTelemetry-based distributed tracing for JVM applications.

## Features

- **OpenTelemetry-Native**: Built on OpenTelemetry standards for maximum compatibility
- **Framework Support**: First-class integrations for Spring Boot, Micronaut, and Quarkus
- **Kotlin-Friendly**: Idiomatic Kotlin extensions and DSL support
- **Zero-Config**: Auto-instrumentation with sensible defaults
- **Flexible**: Manual instrumentation APIs for fine-grained control
- **Production-Ready**: Comprehensive testing, observability, and error handling

## Installation

### Maven

```xml
<dependency>
    <groupId>dev.tracekit</groupId>
    <artifactId>tracekit-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'dev.tracekit:tracekit-spring-boot-starter:1.0.0'
```

## Quick Start

### Spring Boot

```java
// application.properties
tracekit.api-key=your-api-key
tracekit.service-name=my-service
tracekit.environment=production
```

### Kotlin DSL

```kotlin
TraceKit.configure {
    apiKey = "your-api-key"
    serviceName = "my-service"
    environment = "production"
}
```

### Manual Instrumentation

```java
import dev.tracekit.TraceKit;
import io.opentelemetry.api.trace.Span;

public class MyService {
    public void processOrder(Order order) {
        Span span = TraceKit.startSpan("process-order");
        try {
            span.setAttribute("order.id", order.getId());
            span.setAttribute("order.total", order.getTotal());
            // Your business logic here
        } finally {
            span.end();
        }
    }
}
```

## Modules

- **tracekit-core**: Core SDK with OpenTelemetry integration
- **tracekit-spring-boot**: Spring Boot auto-configuration and starter
- **tracekit-kotlin**: Kotlin extensions and DSL
- **tracekit-micronaut**: Micronaut framework integration
- **tracekit-quarkus**: Quarkus framework integration

## Documentation

- [Getting Started Guide](docs/getting-started.md)
- [Configuration Reference](docs/configuration.md)
- [Spring Boot Integration](docs/spring-boot.md)
- [Kotlin Guide](docs/kotlin.md)
- [API Documentation](https://docs.tracekit.dev/java-sdk)

## Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 7.0+

## Support

- Documentation: https://docs.tracekit.dev
- Issues: https://github.com/context-io/tracekit-java-sdk/issues
- Email: support@tracekit.dev

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

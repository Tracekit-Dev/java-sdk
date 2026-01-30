# TraceKit Spring Boot Example

This example demonstrates how to integrate TraceKit APM into a Spring Boot application using the TraceKit Spring Boot Starter.

## Features

This demo application showcases:

- **Zero-Code Instrumentation**: Automatic HTTP request tracing with Spring Boot auto-configuration
- **Distributed Tracing**: OpenTelemetry-based tracing with automatic span creation
- **Error Tracking**: Automatic exception capture and reporting
- **Custom Configuration**: YAML-based configuration for all TraceKit settings
- **Local Development**: Built-in support for the TraceKit Local UI
- **Production Ready**: Easy configuration for cloud deployment

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- TraceKit API key (for cloud reporting) - [Sign up here](https://tracekit.dev)
- Optional: TraceKit Local UI running on `http://localhost:3000` (for local development)

## Quick Start

### 1. Configure Your API Key

Set your TraceKit API key as an environment variable:

```bash
export TRACEKIT_API_KEY=your-api-key-here
```

Or edit `src/main/resources/application.yml` and replace `your-api-key-here` with your actual API key.

### 2. Build the Application

```bash
mvn clean package
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/spring-boot-example-1.0.0-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`.

## Testing the Endpoints

### Welcome Endpoint

```bash
curl http://localhost:8080/
```

Returns a welcome message with available endpoints.

### User Lookup Endpoint

```bash
# Successful lookup
curl http://localhost:8080/users/1
curl http://localhost:8080/users/2
curl http://localhost:8080/users/3

# Not found (404)
curl http://localhost:8080/users/999
```

### Error Simulation Endpoint

```bash
curl http://localhost:8080/error
```

This endpoint randomly throws different exceptions to demonstrate error tracking.

## Viewing Traces

### Local UI (Development)

If you have the TraceKit Local UI running on `http://localhost:3000`:

1. Make requests to the demo endpoints
2. Open `http://localhost:3000` in your browser
3. View real-time traces and performance metrics
4. Inspect span details, timing, and errors

### Cloud Dashboard (Production)

1. Make requests to the demo endpoints
2. Log in to your TraceKit account at [https://app.tracekit.dev](https://app.tracekit.dev)
3. Navigate to the "Traces" section
4. Filter by service name: `spring-boot-demo`
5. Analyze performance, errors, and distributed traces

## Configuration Options

All TraceKit configuration is in `src/main/resources/application.yml` under the `tracekit` section:

### Basic Configuration

```yaml
tracekit:
  api-key: your-api-key-here        # Your TraceKit API key
  service-name: spring-boot-demo    # Service identifier in traces
  environment: development          # Deployment environment
  enabled: true                     # Enable/disable tracing
```

### Sampling Configuration

```yaml
tracekit:
  sampling:
    rate: 1.0  # Sample rate: 0.0 to 1.0 (1.0 = 100% of requests)
```

### Export Configuration

```yaml
tracekit:
  export:
    endpoint: https://api.tracekit.dev/v1/traces  # TraceKit cloud endpoint
    timeout: 30000                                 # Export timeout (ms)
```

### Local UI Configuration

```yaml
tracekit:
  local-ui:
    enabled: true                                  # Enable local UI export
    endpoint: http://localhost:3000/api/traces    # Local UI endpoint
```

### Resource Attributes

Add custom metadata to all traces:

```yaml
tracekit:
  resource:
    attributes:
      deployment.environment: production
      service.version: 1.0.0
      service.namespace: my-company
      team: backend
      region: us-east-1
```

## Environment Variables

You can override configuration using environment variables:

- `TRACEKIT_API_KEY` - Your TraceKit API key
- `TRACEKIT_SERVICE_NAME` - Service name
- `TRACEKIT_ENVIRONMENT` - Environment (development, staging, production)
- `TRACEKIT_ENDPOINT` - Export endpoint
- `TRACEKIT_LOCAL_UI_ENABLED` - Enable/disable local UI (true/false)
- `TRACEKIT_LOCAL_UI_ENDPOINT` - Local UI endpoint

Example:

```bash
export TRACEKIT_API_KEY=tk_live_abc123
export TRACEKIT_SERVICE_NAME=my-spring-app
export TRACEKIT_ENVIRONMENT=production

mvn spring-boot:run
```

## What Gets Traced?

The TraceKit Spring Boot Starter automatically traces:

1. **HTTP Requests**
   - Method, path, status code
   - Request and response headers
   - Query parameters
   - Processing duration

2. **Errors and Exceptions**
   - Exception type and message
   - Stack traces
   - HTTP error status codes

3. **Custom Attributes**
   - Service metadata
   - Environment information
   - Resource attributes

## Project Structure

```
spring-boot-example/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
└── src/
    └── main/
        ├── java/
        │   └── dev/tracekit/example/
        │       └── DemoApplication.java    # Main application class
        └── resources/
            └── application.yml              # Configuration file
```

## Troubleshooting

### Traces Not Appearing

1. **Check API Key**: Ensure your `TRACEKIT_API_KEY` is set correctly
2. **Verify Endpoints**: Check that the export endpoint is accessible
3. **Check Logs**: Look for TraceKit debug logs in the console
4. **Network Issues**: Ensure your application can reach the TraceKit API

### Local UI Not Working

1. **Verify Local UI is Running**: Check `http://localhost:3000`
2. **Check Configuration**: Ensure `tracekit.local-ui.enabled: true`
3. **Port Conflicts**: Make sure port 3000 is not in use by another application

### Build Errors

1. **Java Version**: Ensure you're using Java 11 or higher
2. **Maven Version**: Update to Maven 3.6+
3. **Clean Build**: Run `mvn clean install` in the parent directory first

## Advanced Usage

### Programmatic Configuration

You can also configure TraceKit programmatically in your Spring Boot application:

```java
@Configuration
public class TracekitConfig {
    @Bean
    public TracekitConfigurationCustomizer tracekitCustomizer() {
        return config -> {
            config.setServiceName("custom-service-name");
            config.setEnvironment("custom-environment");
            // Add more customizations
        };
    }
}
```

### Custom Spans

Add custom instrumentation to your code:

```java
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;

@Service
public class MyService {

    @Autowired
    private Tracer tracer;

    public void myMethod() {
        Span span = tracer.spanBuilder("myCustomOperation").startSpan();
        try {
            // Your code here
        } finally {
            span.end();
        }
    }
}
```

## Next Steps

1. **Explore the Code**: Review `DemoApplication.java` to see how simple the integration is
2. **Customize Configuration**: Modify `application.yml` to match your needs
3. **Add to Your Project**: Copy this configuration to your own Spring Boot application
4. **Monitor Production**: Deploy with production API key and monitor real traffic

## Support

- Documentation: [https://docs.tracekit.dev](https://docs.tracekit.dev)
- GitHub: [https://github.com/context-io/tracekit-java-sdk](https://github.com/context-io/tracekit-java-sdk)
- Issues: [https://github.com/context-io/tracekit-java-sdk/issues](https://github.com/context-io/tracekit-java-sdk/issues)

## License

MIT License - See LICENSE file in the root directory.

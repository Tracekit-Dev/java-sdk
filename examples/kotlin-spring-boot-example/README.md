# TraceKit Kotlin Spring Boot Example

This example demonstrates how to integrate TraceKit APM into a Kotlin Spring Boot application using the TraceKit Spring Boot Starter.

## Features

This demo application showcases:

- **Zero-Code Instrumentation**: Automatic HTTP request tracing with Spring Boot auto-configuration
- **Distributed Tracing**: OpenTelemetry-based tracing with automatic span creation
- **Error Tracking**: Automatic exception capture and reporting
- **Security Scanning**: Automatic detection of hardcoded secrets, API keys, and sensitive data
- **Code Monitoring**: Snapshot-based debugging with variable capture
- **Custom Configuration**: YAML-based configuration for all TraceKit settings
- **Kotlin Idiomatic**: Uses Kotlin features like data classes, extension functions, and null safety
- **Local Development**: Built-in support for the TraceKit Local UI
- **Production Ready**: Easy configuration for cloud deployment

## Prerequisites

- Java 11 or higher
- Kotlin 1.9+ (included via Maven)
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
java -jar target/kotlin-spring-boot-example-1.0.0-SNAPSHOT.jar
```

The application will start on `http://localhost:8082` (note: port 8082 to avoid conflicts with the Java example).

## Testing the Endpoints

### Welcome Endpoint

```bash
curl http://localhost:8082/
```

Returns a welcome message with available endpoints.

### User Lookup Endpoint

```bash
# Successful lookup
curl http://localhost:8082/users/1
curl http://localhost:8082/users/2
curl http://localhost:8082/users/3

# Not found (404)
curl http://localhost:8082/users/999
```

### Error Simulation Endpoint

```bash
curl http://localhost:8082/error
```

This endpoint randomly throws different exceptions to demonstrate error tracking.

### Payment Processing Endpoint (Security Scanning Demo)

```bash
curl -X POST http://localhost:8082/process-payment \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 5000,
    "currency": "USD",
    "paymentMethod": "card"
  }'
```

This endpoint demonstrates:
- **Security Scanning**: Automatic detection of hardcoded secrets (Stripe API key, credit card numbers)
- **Code Monitoring**: Snapshot capture with variable inspection
- **Distributed Tracing**: Full request trace with custom spans

When you call this endpoint, TraceKit will:
1. Auto-register a breakpoint at the snapshot capture location
2. Scan variables for sensitive data (API keys, credit cards, tokens)
3. Redact sensitive values before sending to the backend
4. Create security flags for detected issues
5. Link the snapshot to the current trace

**Security Issues Detected** (intentionally included for demo):
- Stripe API key: `sk_test_FakeKey123456789ABCDEFGHIJKL` (CRITICAL severity)
- Credit card number: `4532123456789012` (HIGH severity)

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
4. Filter by service name: `kotlin-sdk`
5. Analyze performance, errors, and distributed traces

## Viewing Code Snapshots

### Enable Code Monitoring

First, ensure code monitoring is enabled in `application.yml`:

```yaml
tracekit:
  enable-code-monitoring: true
```

### Viewing Snapshots in Dashboard

1. Call the payment processing endpoint to trigger snapshot registration:
   ```bash
   curl -X POST http://localhost:8082/process-payment \
     -H "Content-Type: application/json" \
     -d '{"amount": 5000, "currency": "USD", "paymentMethod": "card"}'
   ```

2. Log in to [https://app.tracekit.dev](https://app.tracekit.dev)

3. Navigate to **Code Monitoring** → **Breakpoints**

4. Find the `payment-processing` breakpoint at `DemoApplication.kt:152`

5. **Enable the breakpoint** by clicking the toggle switch

6. Make another request to the endpoint:
   ```bash
   curl -X POST http://localhost:8082/process-payment \
     -H "Content-Type: application/json" \
     -d '{"amount": 10000, "currency": "EUR", "paymentMethod": "card"}'
   ```

7. Navigate to **Code Monitoring** → **Snapshots** to view captured data:
   - Variable values at execution time
   - Security flags for sensitive data detected
   - Stack trace at the capture point
   - Linked distributed trace (trace_id, span_id)

### Security Events

Security scanning automatically creates events when sensitive data is detected:

1. Navigate to **Security** → **Events** in the dashboard

2. Look for events with types:
   - `sensitive_data_stripe_key` (CRITICAL)
   - `sensitive_data_credit_card` (HIGH)

3. Each event includes:
   - Severity level
   - Variable name containing sensitive data
   - File location where it was detected
   - Timestamp and service information
   - Link to the related snapshot

## Configuration Options

All TraceKit configuration is in `src/main/resources/application.yml` under the `tracekit` section:

### Basic Configuration

```yaml
tracekit:
  api-key: your-api-key-here        # Your TraceKit API key
  service-name: kotlin-sdk           # Service identifier in traces
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

### Code Monitoring Configuration

Enable snapshot-based debugging with variable capture:

```yaml
tracekit:
  enable-code-monitoring: true      # Enable snapshot capture (default: true)
```

**How it works**:
- When you call `tracekitSDK.captureSnapshot(label, variables)`, a breakpoint is automatically registered
- Breakpoints must be enabled in the dashboard before they start capturing
- **Security scanning is automatic** - all captured variables are scanned for sensitive data (API keys, passwords, tokens, credit cards)
- Sensitive values are redacted before sending to the backend
- Security flags are created for detected issues

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
export TRACEKIT_SERVICE_NAME=my-kotlin-app
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

## What Gets Monitored?

When code monitoring is enabled, TraceKit provides:

1. **Code Snapshots**
   - Variable values at execution points
   - Stack traces at capture time
   - Function names and line numbers
   - Linked to distributed traces (trace_id, span_id)

2. **Security Scanning**
   - **API Keys**: AWS, Stripe, OpenAI, SendGrid, Twilio, etc.
   - **Tokens**: JWT, OAuth, GitHub, GitLab tokens
   - **Credentials**: Passwords, secrets, private keys
   - **PII**: Credit card numbers, SSNs, email addresses
   - **Severity Levels**: CRITICAL, HIGH, MEDIUM, LOW

3. **Automatic Data Redaction**
   - Sensitive values replaced with `[REDACTED]`
   - Security flags attached to snapshots
   - Original data never leaves your application
   - Safe for production debugging

## Project Structure

```
kotlin-spring-boot-example/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
└── src/
    └── main/
        ├── kotlin/
        │   └── dev/tracekit/example/
        │       └── DemoApplication.kt    # Main application class
        └── resources/
            └── application.yml           # Configuration file
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
4. **Kotlin Compiler**: Ensure Kotlin 1.9+ is being used (check pom.xml)

## Advanced Usage

### Programmatic Configuration

You can also configure TraceKit programmatically in your Spring Boot application:

```kotlin
import dev.tracekit.spring.TracekitConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TracekitConfig {

    @Bean
    fun tracekitCustomizer() = TracekitConfigurationCustomizer { config ->
        config.serviceName = "custom-service-name"
        config.environment = "custom-environment"
        // Add more customizations
    }
}
```

### Custom Spans

Add custom instrumentation to your code:

```kotlin
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.Span
import org.springframework.stereotype.Service

@Service
class MyService(private val tracer: Tracer) {

    fun myMethod() {
        val span = tracer.spanBuilder("myCustomOperation").startSpan()
        span.makeCurrent().use {
            try {
                // Your code here
            } finally {
                span.end()
            }
        }
    }
}
```

### Code Monitoring with Snapshots

Capture variable state at specific execution points:

```kotlin
import dev.tracekit.TracekitSDK
import org.springframework.stereotype.Service

@Service
class PaymentService(private val tracekitSDK: TracekitSDK) {

    fun processPayment(customerId: String, amount: Double): Payment {
        // Collect variables you want to inspect
        val variables = mapOf(
            "customerId" to customerId,
            "amount" to amount,
            "timestamp" to System.currentTimeMillis()
        )

        // Capture snapshot - auto-registers breakpoint on first call
        tracekitSDK.captureSnapshot("payment-start", variables)

        val payment = executePayment(customerId, amount)

        // Capture another snapshot at a different point
        val resultVars = mapOf(
            "paymentId" to payment.id,
            "status" to payment.status,
            "processingTime" to payment.processingTimeMs
        )

        tracekitSDK.captureSnapshot("payment-complete", resultVars)

        return payment
    }

    private fun executePayment(customerId: String, amount: Double): Payment {
        // Payment processing logic...
        return Payment()
    }
}
```

**How it works**:
1. First call to `captureSnapshot()` with a label auto-registers the breakpoint
2. Breakpoint appears in the dashboard at the file and line number where it was called
3. Enable the breakpoint in the dashboard to start capturing
4. Next calls will capture and send variable snapshots
5. Security scanning automatically detects and redacts sensitive data
6. Snapshots are linked to the current distributed trace

**Best Practices**:
- Use descriptive labels: `"user-login"`, `"payment-processing"`, `"data-transform"`
- Capture relevant variables only (avoid capturing entire request objects)
- Be cautious with sensitive data - the scanner will redact known patterns, but review carefully
- Disable breakpoints in production when not actively debugging
- Use Kotlin's null safety to avoid capturing null values

**Kotlin-Specific Tips**:
- Use `mapOf()` for creating variable maps (more idiomatic than HashMap)
- Leverage data classes for structured snapshot data
- Use extension functions to wrap snapshot capture for cleaner code
- Kotlin's type inference makes the API more concise

## Kotlin vs Java SDK

Both examples use the **same TraceKit SDK** (written in Java). The SDK is 100% compatible with Kotlin thanks to JVM interoperability. The Kotlin example demonstrates:

- **Kotlin Syntax**: Using `mapOf()`, data classes, and extension functions
- **Null Safety**: Leveraging Kotlin's type system for safer code
- **Conciseness**: More compact code compared to Java
- **Spring Boot Integration**: Same Spring Boot auto-configuration works seamlessly

Choose the language that fits your project - the TraceKit SDK works identically in both!

## Next Steps

1. **Explore the Code**: Review `DemoApplication.kt` to see Kotlin-idiomatic integration
2. **Customize Configuration**: Modify `application.yml` to match your needs
3. **Add to Your Project**: Copy this configuration to your own Kotlin Spring Boot application
4. **Monitor Production**: Deploy with production API key and monitor real traffic
5. **Learn More**: Check out the Java example for comparison and additional patterns

## Support

- Documentation: [https://docs.tracekit.dev](https://docs.tracekit.dev)
- GitHub: [https://github.com/context-io/tracekit-java-sdk](https://github.com/context-io/tracekit-java-sdk)
- Issues: [https://github.com/context-io/tracekit-java-sdk/issues](https://github.com/context-io/tracekit-java-sdk/issues)

## License

MIT License - See LICENSE file in the root directory.

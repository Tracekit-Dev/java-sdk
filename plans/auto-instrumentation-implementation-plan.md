# Java SDK Auto-Instrumentation Implementation Plan

**Status**: Planning
**Last Updated**: 2026-02-04
**Version**: 1.0

## Executive Summary

This document outlines the strategy and implementation plan for adding comprehensive auto-instrumentation capabilities to the TraceKit Java SDK, bringing it to feature parity with the Node.js and Ruby SDKs. The plan includes database tracing (JDBC, PostgreSQL, MySQL, MongoDB), HTTP client instrumentation (RestTemplate, WebClient, OkHttp, Apache HttpClient), messaging systems (Kafka, RabbitMQ), and caching layers (Redis, Memcached).

## Background

### Current State Analysis

**Java SDK (v1.3.1) - Current Capabilities:**
- ✅ HTTP request tracing (Spring Boot only, via `TracekitWebMvcConfigurer` interceptor)
- ✅ Manual span creation via OpenTelemetry API
- ✅ Custom metrics (Counter, Gauge, Histogram)
- ✅ Snapshot capture with security scanning
- ❌ No automatic database instrumentation
- ❌ No automatic HTTP client instrumentation
- ❌ No automatic messaging instrumentation
- ❌ No automatic cache instrumentation

**Node.js SDK - Reference Implementation:**
- ✅ Auto-instruments: PostgreSQL, MySQL, MongoDB, Redis
- ✅ Auto-instruments: HTTP, HTTPS, Fetch API, Axios
- ✅ Method: OpenTelemetry libraries with monkey-patching
- ✅ Code Location: `tracekit/node-apm/src/client.ts` (lines 433-444 for DB, 407-430 for HTTP)

**Ruby SDK - Reference Implementation:**
- ✅ Auto-instruments: PostgreSQL (pg gem), MySQL2, Redis
- ✅ Auto-instruments: ActiveRecord, Net::HTTP, HTTP gem
- ✅ Method: OpenTelemetry instrumentation gems with method prepending
- ✅ Code Location: `tracekit/ruby-sdk/lib/tracekit/sdk.rb` (lines 189-215)

### Gap Analysis

| Feature Category | Node.js | Ruby | Java SDK | Gap |
|-----------------|---------|------|----------|-----|
| **Database** |
| PostgreSQL | ✅ | ✅ | ❌ | Missing |
| MySQL | ✅ | ✅ | ❌ | Missing |
| MongoDB | ✅ | ❌ | ❌ | Missing |
| Redis | ✅ | ✅ | ❌ | Missing |
| JDBC Generic | N/A | N/A | ❌ | Missing |
| **HTTP Clients** |
| Built-in HTTP | ✅ | ✅ | ❌ | Missing |
| Popular Libraries | ✅ | ✅ | ❌ | Missing |
| **Frameworks** |
| Framework Integration | ✅ | ✅ | ⚠️ Partial | Spring only, needs expansion |
| **Messaging** |
| Message Queues | ❌ | ❌ | ❌ | All platforms missing |

## Technical Approach

### Why Not Java Agent?

**Java Agent Approach** (Bytecode Manipulation):
- ✅ Pros: Zero code changes, works with any framework, comprehensive coverage
- ❌ Cons: Requires JVM startup flag (`-javaagent`), more complex deployment, harder debugging
- ✅ OpenTelemetry provides: `opentelemetry-javaagent.jar` (all-in-one instrumentation)
- 📌 **Use Case**: Best for vanilla Java apps without Spring Boot

**Library-Based Approach** (Recommended for TraceKit):
- ✅ Pros: Simpler deployment, programmatic control, better IDE support, easier debugging
- ✅ Works identically to Node.js and Ruby SDKs (dependency-based)
- ✅ Leverages Spring Boot's auto-configuration capabilities
- 📌 **Use Case**: Primary approach for `tracekit-spring-boot-starter`

### Implementation Strategy

**Hybrid Approach:**
1. **Library-based auto-instrumentation** for `tracekit-spring-boot-starter` (PRIMARY)
2. **Document OpenTelemetry Java Agent** usage for `tracekit-core` users (SECONDARY)
3. **Optional custom agent module** (`tracekit-agent`) if demand exists (FUTURE)

This mirrors how the ecosystem works:
- Node.js: Uses OpenTelemetry libraries (e.g., `@opentelemetry/instrumentation-pg`)
- Ruby: Uses OpenTelemetry instrumentation gems (e.g., `opentelemetry-instrumentation-pg`)
- Java: Will use OpenTelemetry instrumentation libraries (e.g., `opentelemetry-instrumentation-jdbc`)

## Implementation Phases

### Phase 1: JDBC Database Instrumentation (Spring Boot)

**Priority**: HIGH
**Estimated Effort**: 1-2 weeks
**Target Module**: `tracekit-spring-boot-starter`

#### Dependencies to Add

```xml
<!-- JDBC Instrumentation -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jdbc</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- Optional: Specific database instrumentations -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-hikaricp-3.0</artifactId>
    <version>2.0.0-alpha</version>
</dependency>
```

#### Implementation Steps

1. **Create `TracekitJdbcAutoConfiguration.java`**:
   ```java
   @Configuration
   @ConditionalOnProperty(name = "tracekit.auto-instrumentation.jdbc.enabled",
                          havingValue = "true", matchIfMissing = true)
   @AutoConfigureAfter(TracekitAutoConfiguration.class)
   public class TracekitJdbcAutoConfiguration {

       @Bean
       @ConditionalOnBean(DataSource.class)
       public DataSource instrumentedDataSource(DataSource dataSource,
                                                OpenTelemetry openTelemetry) {
           // Wrap DataSource with OpenTelemetry JDBC instrumentation
           return JdbcTelemetry.create(openTelemetry)
               .wrap(dataSource);
       }

       @Bean
       @ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
       public HikariDataSourceInstrumenter hikariInstrumenter(OpenTelemetry openTelemetry) {
           return new HikariDataSourceInstrumenter(openTelemetry);
       }
   }
   ```

2. **Add Configuration Properties** (`TracekitProperties.java`):
   ```java
   public static class AutoInstrumentation {
       private Jdbc jdbc = new Jdbc();

       public static class Jdbc {
           private boolean enabled = true;
           private boolean captureStatements = true;
           private boolean sanitizeStatements = true;

           // Getters/setters
       }
   }
   ```

3. **Update `spring.factories`**:
   ```properties
   org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
   dev.tracekit.spring.TracekitAutoConfiguration,\
   dev.tracekit.spring.TracekitJdbcAutoConfiguration
   ```

#### Expected Behavior

**Before**:
```java
@Service
public class UserService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public User findById(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE id = ?",
            new UserRowMapper(), id);
    }
}
// No automatic database span created
```

**After**:
```java
// Same code - no changes needed!
@Service
public class UserService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public User findById(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE id = ?",
            new UserRowMapper(), id);
    }
}
// Automatically creates span:
// - Name: "SELECT users"
// - Attributes: db.system=postgresql, db.statement=SELECT * FROM users WHERE id = ?, db.operation=SELECT
```

#### Testing Requirements

- PostgreSQL integration test with Spring Boot
- MySQL integration test with Spring Boot
- H2 in-memory database test
- HikariCP connection pool instrumentation test
- Statement sanitization test (no sensitive data in spans)

### Phase 2: HTTP Client Instrumentation (Spring Boot)

**Priority**: HIGH
**Estimated Effort**: 1-2 weeks
**Target Module**: `tracekit-spring-boot-starter`

#### Dependencies to Add

```xml
<!-- RestTemplate Instrumentation -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-web-3.1</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- WebClient Instrumentation (Reactive) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-webflux-5.3</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- OkHttp Instrumentation -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-okhttp-3.0</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- Apache HttpClient Instrumentation -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-httpclient-5.0</artifactId>
    <version>2.0.0-alpha</version>
</dependency>
```

#### Implementation Steps

1. **Create `TracekitHttpClientAutoConfiguration.java`**:
   ```java
   @Configuration
   @ConditionalOnProperty(name = "tracekit.auto-instrumentation.http-client.enabled",
                          havingValue = "true", matchIfMissing = true)
   @AutoConfigureAfter(TracekitAutoConfiguration.class)
   public class TracekitHttpClientAutoConfiguration {

       @Bean
       @ConditionalOnClass(RestTemplate.class)
       public BeanPostProcessor restTemplateInstrumenter(OpenTelemetry openTelemetry) {
           return new BeanPostProcessor() {
               @Override
               public Object postProcessAfterInitialization(Object bean, String beanName) {
                   if (bean instanceof RestTemplate) {
                       RestTemplate restTemplate = (RestTemplate) bean;
                       SpringWebTelemetry telemetry = SpringWebTelemetry.create(openTelemetry);
                       restTemplate.getInterceptors().add(
                           telemetry.newInterceptor()
                       );
                   }
                   return bean;
               }
           };
       }

       @Bean
       @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
       public WebClientCustomizer webClientInstrumenter(OpenTelemetry openTelemetry) {
           SpringWebTelemetry telemetry = SpringWebTelemetry.create(openTelemetry);
           return builder -> builder.filter(telemetry.newExchangeFilterFunction());
       }

       @Bean
       @ConditionalOnClass(name = "okhttp3.OkHttpClient")
       public OkHttpClient instrumentedOkHttpClient(OpenTelemetry openTelemetry) {
           OkHttpTelemetry telemetry = OkHttpTelemetry.create(openTelemetry);
           return new OkHttpClient.Builder()
               .addInterceptor(telemetry.newInterceptor())
               .build();
       }
   }
   ```

2. **Add Configuration Properties**:
   ```java
   public static class HttpClient {
       private boolean enabled = true;
       private boolean captureHeaders = false;
       private boolean captureBodies = false;
       private Set<String> allowedHeaders = new HashSet<>();

       // Getters/setters
   }
   ```

#### Expected Behavior

**Before**:
```java
@Service
public class PaymentService {
    @Autowired
    private RestTemplate restTemplate;

    public PaymentResponse charge(PaymentRequest request) {
        return restTemplate.postForObject(
            "https://api.stripe.com/v1/charges",
            request,
            PaymentResponse.class);
    }
}
// No automatic HTTP client span
```

**After**:
```java
// Same code - no changes needed!
@Service
public class PaymentService {
    @Autowired
    private RestTemplate restTemplate;

    public PaymentResponse charge(PaymentRequest request) {
        return restTemplate.postForObject(
            "https://api.stripe.com/v1/charges",
            request,
            PaymentResponse.class);
    }
}
// Automatically creates span:
// - Name: "POST"
// - Attributes: http.method=POST, http.url=https://api.stripe.com/v1/charges,
//               http.status_code=200, net.peer.name=api.stripe.com
// - Parent: Current active span
// - Propagates W3C Trace Context headers to downstream service
```

#### Testing Requirements

- RestTemplate outbound call test with trace propagation
- WebClient (reactive) outbound call test
- OkHttp client test
- Apache HttpClient test
- Header propagation test (W3C Trace Context)
- Error handling test (4xx, 5xx responses)

### Phase 3: Redis Instrumentation (Spring Boot)

**Priority**: MEDIUM
**Estimated Effort**: 1 week
**Target Module**: `tracekit-spring-boot-starter`

#### Dependencies to Add

```xml
<!-- Lettuce (Spring Data Redis default) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-lettuce-5.1</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- Jedis (alternative Redis client) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jedis-4.0</artifactId>
    <version>2.0.0-alpha</version>
</dependency>
```

#### Implementation Steps

1. **Create `TracekitRedisAutoConfiguration.java`**:
   ```java
   @Configuration
   @ConditionalOnProperty(name = "tracekit.auto-instrumentation.redis.enabled",
                          havingValue = "true", matchIfMissing = true)
   @ConditionalOnClass(RedisConnectionFactory.class)
   @AutoConfigureAfter(TracekitAutoConfiguration.class)
   public class TracekitRedisAutoConfiguration {

       @Bean
       @ConditionalOnClass(name = "io.lettuce.core.RedisClient")
       public LettuceClientResourcesBuilderCustomizer lettuceInstrumenter(
               OpenTelemetry openTelemetry) {
           LettuceTelemetry telemetry = LettuceTelemetry.create(openTelemetry);
           return builder -> builder.tracing(telemetry.newTracing());
       }

       @Bean
       @ConditionalOnClass(name = "redis.clients.jedis.Jedis")
       public JedisClientConfigBuilderCustomizer jedisInstrumenter(
               OpenTelemetry openTelemetry) {
           return builder -> {
               // Wrap Jedis with OpenTelemetry instrumentation
               JedisTelemetry telemetry = JedisTelemetry.create(openTelemetry);
               return telemetry.wrap(builder);
           };
       }
   }
   ```

#### Expected Behavior

```java
@Service
public class CacheService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void cacheUser(User user) {
        redisTemplate.opsForValue().set(
            "user:" + user.getId(),
            objectMapper.writeValueAsString(user));
    }
}
// Automatically creates span:
// - Name: "SET"
// - Attributes: db.system=redis, db.operation=SET, db.redis.key=user:123
```

### Phase 4: Messaging Instrumentation (Spring Boot)

**Priority**: MEDIUM
**Estimated Effort**: 1-2 weeks
**Target Module**: `tracekit-spring-boot-starter`

#### Dependencies to Add

```xml
<!-- Kafka -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-kafka-clients-2.6</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- RabbitMQ -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-rabbitmq-2.0</artifactId>
    <version>2.0.0-alpha</version>
</dependency>

<!-- Spring JMS (Optional) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jms-2.0</artifactId>
    <version>2.0.0-alpha</version>
</dependency>
```

#### Implementation Steps

1. **Create `TracekitMessagingAutoConfiguration.java`**:
   ```java
   @Configuration
   @ConditionalOnProperty(name = "tracekit.auto-instrumentation.messaging.enabled",
                          havingValue = "true", matchIfMissing = true)
   @AutoConfigureAfter(TracekitAutoConfiguration.class)
   public class TracekitMessagingAutoConfiguration {

       @Bean
       @ConditionalOnClass(name = "org.apache.kafka.clients.producer.ProducerInterceptor")
       public ProducerFactory<?, ?> instrumentedKafkaProducerFactory(
               ProducerFactory<?, ?> producerFactory,
               OpenTelemetry openTelemetry) {
           KafkaTelemetry telemetry = KafkaTelemetry.create(openTelemetry);
           return new InstrumentedProducerFactory<>(producerFactory, telemetry);
       }

       @Bean
       @ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
       public RabbitTemplate instrumentedRabbitTemplate(
               ConnectionFactory connectionFactory,
               OpenTelemetry openTelemetry) {
           RabbitMqTelemetry telemetry = RabbitMqTelemetry.create(openTelemetry);
           return telemetry.wrap(new RabbitTemplate(connectionFactory));
       }
   }
   ```

#### Expected Behavior

```java
@Service
public class NotificationService {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendNotification(String userId, String message) {
        kafkaTemplate.send("notifications", userId, message);
    }
}
// Automatically creates span:
// - Name: "notifications send"
// - Attributes: messaging.system=kafka, messaging.destination=notifications,
//               messaging.operation=send
// - Propagates trace context in Kafka headers
```

### Phase 5: MongoDB Instrumentation (Spring Boot)

**Priority**: LOW
**Estimated Effort**: 1 week
**Target Module**: `tracekit-spring-boot-starter`

#### Dependencies to Add

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-mongo-3.1</artifactId>
    <version>2.0.0-alpha</version>
</dependency>
```

#### Implementation Steps

1. **Create `TracekitMongoAutoConfiguration.java`**:
   ```java
   @Configuration
   @ConditionalOnProperty(name = "tracekit.auto-instrumentation.mongodb.enabled",
                          havingValue = "true", matchIfMissing = true)
   @ConditionalOnClass(MongoClient.class)
   @AutoConfigureAfter(TracekitAutoConfiguration.class)
   public class TracekitMongoAutoConfiguration {

       @Bean
       public MongoClientSettingsBuilderCustomizer mongoInstrumenter(
               OpenTelemetry openTelemetry) {
           MongoTelemetry telemetry = MongoTelemetry.create(openTelemetry);
           return builder -> builder.addCommandListener(
               telemetry.newCommandListener());
       }
   }
   ```

### Phase 6: Java Agent Documentation (Vanilla Java)

**Priority**: HIGH
**Estimated Effort**: 3-5 days
**Target Module**: `tracekit-core` + Documentation

#### Deliverables

1. **Update `README.md`** with Java Agent section:
   ```markdown
   ## Java Agent Auto-Instrumentation (Vanilla Java)

   For vanilla Java applications (non-Spring Boot), TraceKit recommends using the
   OpenTelemetry Java Agent for automatic instrumentation.

   ### Download Agent

   ```bash
   wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
   ```

   ### Configure TraceKit Exporter

   Create `otel-config.properties`:
   ```properties
   otel.service.name=my-service
   otel.traces.exporter=otlp
   otel.exporter.otlp.endpoint=https://api.tracekit.dev/v1/traces
   otel.exporter.otlp.headers=x-api-key=YOUR_TRACEKIT_API_KEY
   ```

   ### Run Application

   ```bash
   java -javaagent:opentelemetry-javaagent.jar \
        -Dotel.javaagent.configuration-file=otel-config.properties \
        -jar your-application.jar
   ```

   This automatically instruments:
   - JDBC databases (PostgreSQL, MySQL, Oracle, etc.)
   - HTTP clients (Apache HttpClient, OkHttp, java.net.http)
   - Kafka, RabbitMQ, JMS
   - Redis (Jedis, Lettuce)
   - MongoDB
   - And 100+ other libraries
   ```

2. **Create `docs/java-agent-integration.md`**:
   - Detailed integration guide
   - Environment variable configuration
   - Troubleshooting section
   - Performance tuning guide
   - How to combine with TraceKit SDK features (metrics, snapshots)

3. **Add Example Project**: `examples/vanilla-java-with-agent/`
   - Simple Java application
   - JDBC database access
   - HTTP client calls
   - Docker Compose setup with PostgreSQL
   - Shell scripts for running with agent

### Phase 7: Optional Custom Agent Module

**Priority**: FUTURE
**Estimated Effort**: 3-4 weeks
**Target Module**: New module `tracekit-agent`

Only implement if:
- Users request TraceKit-specific agent features
- Need custom bytecode instrumentation for proprietary libraries
- Want to bundle agent with TraceKit branding

## Configuration Reference

### Spring Boot Properties (After All Phases)

```yaml
tracekit:
  enabled: true
  api-key: ${TRACEKIT_API_KEY}
  service-name: my-service
  environment: production

  # Auto-instrumentation controls
  auto-instrumentation:
    jdbc:
      enabled: true
      capture-statements: true
      sanitize-statements: true
      max-statement-length: 2000

    http-client:
      enabled: true
      capture-headers: false
      capture-bodies: false
      allowed-headers:
        - Content-Type
        - User-Agent

    redis:
      enabled: true
      capture-keys: true
      max-key-length: 100

    messaging:
      enabled: true
      capture-headers: true
      capture-payloads: false

    mongodb:
      enabled: true
      capture-queries: true
      sanitize-queries: true
```

## Testing Strategy

### Unit Tests
- Individual instrumentation configuration classes
- Property binding tests
- Conditional bean creation tests

### Integration Tests
- Full Spring Boot application with all instrumentations enabled
- Real database, Redis, Kafka containers (TestContainers)
- Trace validation (span names, attributes, parent-child relationships)
- Performance benchmarks (overhead < 5%)

### End-to-End Tests
- Multi-service distributed trace test
- Trace context propagation across HTTP boundaries
- Trace context propagation across Kafka topics
- Local UI detection test

## Performance Considerations

### Expected Overhead

| Instrumentation Type | Overhead | Mitigation |
|---------------------|----------|------------|
| JDBC | 1-3% | Statement caching, batch flushing |
| HTTP Client | 1-2% | Header injection optimization |
| Redis | < 1% | Command batching |
| Kafka | 2-4% | Async header injection |
| Overall | < 5% | Sampling, batch exports |

### Optimization Strategies

1. **Batch Span Export**: Export spans in batches (default: 512 spans or 5 seconds)
2. **Sampling**: Implement head-based sampling for high-throughput services
3. **Attribute Limits**: Cap attribute value lengths (default: 2000 chars)
4. **Async Processing**: All instrumentation should be non-blocking

## Documentation Updates

### Files to Update

1. **README.md**:
   - Add "Auto-Instrumentation" section
   - Update feature list
   - Add configuration examples

2. **New Files**:
   - `docs/auto-instrumentation.md` (detailed guide)
   - `docs/java-agent-integration.md` (vanilla Java guide)
   - `examples/auto-instrumentation-demo/` (complete example)

3. **Javadoc**:
   - Document all new configuration classes
   - Add instrumentation behavior notes to `TracekitSDK` class

## Migration Guide

### For Existing Users

**No Breaking Changes**:
- All auto-instrumentation is opt-in (enabled by default but can be disabled)
- Existing manual instrumentation continues to work
- No API changes to core SDK

**Opt-Out Example**:
```yaml
tracekit:
  auto-instrumentation:
    jdbc:
      enabled: false  # Disable if using manual JDBC instrumentation
```

## Success Metrics

### Feature Parity with Node.js/Ruby SDKs

- ✅ Automatic database tracing (JDBC, PostgreSQL, MySQL, MongoDB)
- ✅ Automatic HTTP client tracing (RestTemplate, WebClient, OkHttp)
- ✅ Automatic cache tracing (Redis)
- ✅ Automatic messaging tracing (Kafka, RabbitMQ)
- ✅ Zero-code-change deployment (Spring Boot)
- ✅ Java Agent documentation (vanilla Java)

### Quality Gates

- ✅ All instrumentation types have integration tests
- ✅ Performance overhead < 5%
- ✅ 100% backward compatibility
- ✅ Documentation coverage for all features
- ✅ Example applications for each instrumentation type

## Timeline

| Phase | Duration | Start Date | End Date |
|-------|----------|------------|----------|
| Phase 1: JDBC | 2 weeks | TBD | TBD |
| Phase 2: HTTP Clients | 2 weeks | TBD | TBD |
| Phase 3: Redis | 1 week | TBD | TBD |
| Phase 4: Messaging | 2 weeks | TBD | TBD |
| Phase 5: MongoDB | 1 week | TBD | TBD |
| Phase 6: Documentation | 1 week | TBD | TBD |
| **Total** | **9 weeks** | TBD | TBD |

## Dependencies

### Maven Coordinates

All OpenTelemetry instrumentation libraries:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-instrumentation-bom</artifactId>
            <version>2.0.0-alpha</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Open Questions

1. **Sampling Strategy**: Should we implement head-based sampling by default for high-throughput services?
2. **Body Capture**: Should we capture HTTP request/response bodies (with PII scanning)?
3. **Custom Agent**: Is there demand for a TraceKit-branded Java Agent?
4. **Kotlin DSL**: Should Phase 1 include Kotlin-specific configuration DSL?

## References

- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [OpenTelemetry Java SDK](https://github.com/open-telemetry/opentelemetry-java)
- [Spring Boot OpenTelemetry Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.export.otlp)
- [Node.js SDK Implementation](file:///Users/ogbemudiaterryosayawe/Code/context.io/tracekit/node-apm/src/client.ts)
- [Ruby SDK Implementation](file:///Users/ogbemudiaterryosayawe/Code/context.io/tracekit/ruby-sdk/lib/tracekit/sdk.rb)

## Appendix: Code Examples

### A. JDBC Auto-Instrumentation Example

**Before (Manual)**:
```java
@Service
public class OrderService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TracekitSDK tracekitSDK;

    public List<Order> findByUserId(Long userId) {
        Tracer tracer = tracekitSDK.getTracer("order-service");
        Span span = tracer.spanBuilder("db.query.orders").startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("db.system", "postgresql");
            span.setAttribute("db.operation", "SELECT");
            span.setAttribute("db.statement", "SELECT * FROM orders WHERE user_id = ?");

            List<Order> orders = jdbcTemplate.query(
                "SELECT * FROM orders WHERE user_id = ?",
                new OrderRowMapper(),
                userId
            );

            span.setAttribute("db.result.count", orders.size());
            return orders;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**After (Automatic)**:
```java
@Service
public class OrderService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // No TracekitSDK needed for database tracing!

    public List<Order> findByUserId(Long userId) {
        // Zero instrumentation code needed
        return jdbcTemplate.query(
            "SELECT * FROM orders WHERE user_id = ?",
            new OrderRowMapper(),
            userId
        );
        // Automatic span created with all attributes
    }
}
```

### B. HTTP Client Auto-Instrumentation Example

**Before (Manual)**:
```java
@Service
public class StripeService {
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TracekitSDK tracekitSDK;

    public Charge createCharge(ChargeRequest request) {
        Tracer tracer = tracekitSDK.getTracer("stripe-service");
        Span span = tracer.spanBuilder("stripe.create_charge").startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("http.method", "POST");
            span.setAttribute("http.url", "https://api.stripe.com/v1/charges");
            span.setAttribute("charge.amount", request.getAmount());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            // Manual trace context propagation
            span.getSpanContext().getTraceId(); // Complex!

            HttpEntity<ChargeRequest> entity = new HttpEntity<>(request, headers);
            Charge charge = restTemplate.postForObject(
                "https://api.stripe.com/v1/charges",
                entity,
                Charge.class
            );

            span.setAttribute("charge.id", charge.getId());
            return charge;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**After (Automatic)**:
```java
@Service
public class StripeService {
    @Autowired
    private RestTemplate restTemplate;

    // No TracekitSDK needed for HTTP client tracing!

    public Charge createCharge(ChargeRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        // Trace context automatically propagated!

        HttpEntity<ChargeRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(
            "https://api.stripe.com/v1/charges",
            entity,
            Charge.class
        );
        // Automatic span + trace propagation
    }
}
```

### C. Distributed Tracing Example

**Scenario**: Order Service → Payment Service → Stripe API

```java
// Order Service (Spring Boot with auto-instrumentation)
@RestController
public class OrderController {
    @Autowired
    private PaymentServiceClient paymentClient;

    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request) {
        // Span 1: HTTP request (auto)
        Order order = orderService.save(request);
        // Span 2: JDBC INSERT (auto)

        PaymentResult payment = paymentClient.charge(order.getTotal());
        // Span 3: HTTP client call to Payment Service (auto + propagation)

        return order;
    }
}

// Payment Service (Spring Boot with auto-instrumentation)
@RestController
public class PaymentController {
    @Autowired
    private StripeService stripeService;

    @PostMapping("/charge")
    public PaymentResult charge(@RequestBody ChargeRequest request) {
        // Span 4: HTTP request (auto, parent = Span 3)

        Charge charge = stripeService.createCharge(request);
        // Span 5: HTTP client call to Stripe (auto + propagation)

        return new PaymentResult(charge.getId());
    }
}
```

**Resulting Trace**:
```
Trace ID: 7f8a3b2c1d4e5f6a7b8c9d0e1f2a3b4c

Span 1: POST /orders (Order Service)
  ├─ Span 2: INSERT INTO orders (JDBC, auto)
  └─ Span 3: POST http://payment-service/charge (RestTemplate, auto)
      └─ Span 4: POST /charge (Payment Service)
          └─ Span 5: POST https://api.stripe.com/v1/charges (RestTemplate, auto)
```

All spans automatically created, linked, and exported to TraceKit - zero manual instrumentation!

## Appendix: Comparison with Other SDKs

### Node.js SDK Auto-Instrumentation Implementation

**Location**: `tracekit/node-apm/src/client.ts`

**Database Instrumentation** (lines 433-444):
```typescript
// PostgreSQL
import { PgInstrumentation } from '@opentelemetry/instrumentation-pg';
const pgInstrumentation = new PgInstrumentation();

// MySQL
import { MySQL2Instrumentation } from '@opentelemetry/instrumentation-mysql2';
const mysqlInstrumentation = new MySQL2Instrumentation();

// MongoDB
import { MongoDBInstrumentation } from '@opentelemetry/instrumentation-mongodb';
const mongoInstrumentation = new MongoDBInstrumentation();

// Redis
import { RedisInstrumentation } from '@opentelemetry/instrumentation-redis-4';
const redisInstrumentation = new RedisInstrumentation();
```

**HTTP Instrumentation** (lines 407-430):
```typescript
import { HttpInstrumentation } from '@opentelemetry/instrumentation-http';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';

const httpInstrumentation = new HttpInstrumentation({
  requestHook: (span, request) => {
    span.setAttribute('http.client_ip', getClientIp(request));
  }
});
```

### Ruby SDK Auto-Instrumentation Implementation

**Location**: `tracekit/ruby-sdk/lib/tracekit/sdk.rb`

**Database Instrumentation** (lines 189-215):
```ruby
# PostgreSQL (pg gem)
require 'opentelemetry-instrumentation-pg'
OpenTelemetry::Instrumentation::PG::Instrumentation.instance.install

# MySQL (mysql2 gem)
require 'opentelemetry-instrumentation-mysql2'
OpenTelemetry::Instrumentation::Mysql2::Instrumentation.instance.install

# Redis
require 'opentelemetry-instrumentation-redis'
OpenTelemetry::Instrumentation::Redis::Instrumentation.instance.install

# ActiveRecord
require 'opentelemetry-instrumentation-active_record'
OpenTelemetry::Instrumentation::ActiveRecord::Instrumentation.instance.install

# HTTP clients
require 'opentelemetry-instrumentation-net_http'
OpenTelemetry::Instrumentation::NetHTTP::Instrumentation.instance.install
```

### Key Insights

1. **Both Node.js and Ruby use OpenTelemetry instrumentation libraries** (not agents)
2. **Java SDK should follow the same pattern** using `io.opentelemetry.instrumentation` packages
3. **Spring Boot's dependency injection** makes this even easier than Node.js/Ruby
4. **Java Agent is supplementary**, not the primary approach

---

**Document Version**: 1.0
**Last Updated**: 2026-02-04
**Author**: TraceKit Engineering Team
**Status**: Ready for Implementation

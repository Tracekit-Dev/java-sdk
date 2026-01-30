package dev.tracekit.example

import dev.tracekit.TracekitSDK
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.random.Random

@SpringBootApplication
@RestController
class DemoApplication(private val tracekitSDK: TracekitSDK) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val tracer: Tracer = tracekitSDK.getTracer("kotlin-demo-application")

    init {
        logger.info("TraceKit SDK injected and tracer initialized")
    }

    @GetMapping("/")
    fun welcome(): Map<String, Any> {
        val span = tracer.spanBuilder("GET /").startSpan()
        return span.makeCurrent().use {
            try {
                logger.info("Welcome endpoint called")

                span.setAttribute("http.method", "GET")
                span.setAttribute("http.route", "/")

                mapOf(
                    "message" to "Welcome to TraceKit Kotlin Spring Boot Example!",
                    "version" to "1.0.0",
                    "language" to "Kotlin",
                    "endpoints" to listOf(
                        "GET / - This welcome message",
                        "GET /users/{id} - Get user by ID (try 1, 2, or 3)",
                        "GET /error - Simulate an error for testing",
                        "POST /process-payment - Payment processing with security scanning demo"
                    )
                ).also {
                    span.setStatus(StatusCode.OK)
                }
            } finally {
                span.end()
            }
        }
    }

    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: String): ResponseEntity<*> {
        val span = tracer.spanBuilder("GET /users/{id}").startSpan()
        return span.makeCurrent().use {
            try {
                logger.info("Looking up user with ID: $id")

                span.setAttribute("http.method", "GET")
                span.setAttribute("http.route", "/users/{id}")
                span.setAttribute("user.id", id)

                // Simulate processing delay
                simulateProcessing()

                val user = USERS[id]
                if (user == null) {
                    logger.warn("User not found: $id")
                    span.setAttribute("user.found", false)
                    span.setAttribute("http.status_code", 404)
                    span.setStatus(StatusCode.OK)

                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        mapOf("error" to "User not found", "userId" to id)
                    )
                } else {
                    logger.info("User found: ${user.name}")
                    span.setAttribute("user.found", true)
                    span.setAttribute("user.name", user.name)
                    span.setAttribute("http.status_code", 200)
                    span.setStatus(StatusCode.OK)

                    ResponseEntity.ok(user)
                }
            } finally {
                span.end()
            }
        }
    }

    @GetMapping("/error")
    fun simulateError(): ResponseEntity<*> {
        val span = tracer.spanBuilder("GET /error").startSpan()
        return span.makeCurrent().use {
            try {
                logger.error("Error endpoint called - simulating exception")

                span.setAttribute("http.method", "GET")
                span.setAttribute("http.route", "/error")

                val exception = when (Random.nextInt(3)) {
                    0 -> RuntimeException("Simulated runtime exception for testing")
                    1 -> IllegalStateException("Simulated illegal state for testing")
                    else -> NullPointerException("Simulated null pointer exception for testing")
                }

                span.recordException(exception)
                span.setStatus(StatusCode.ERROR, exception.message)
                throw exception
            } finally {
                span.end()
            }
        }
    }

    @PostMapping("/process-payment")
    fun processPayment(@RequestBody request: Map<String, Any>): ResponseEntity<*> {
        val span = tracer.spanBuilder("POST /process-payment").startSpan()
        return span.makeCurrent().use {
            try {
                logger.info("Payment processing endpoint called")

                span.setAttribute("http.method", "POST")
                span.setAttribute("http.route", "/process-payment")

                val amount = (request["amount"] as? Number)?.toDouble() ?: 0.0
                val currency = request["currency"] as? String ?: "USD"
                val paymentMethod = request["paymentMethod"] as? String ?: "card"

                span.setAttribute("payment.amount", amount)
                span.setAttribute("payment.currency", currency)
                span.setAttribute("payment.method", paymentMethod)

                // Intentionally include security issues for demonstration
                val stripeApiKey = "sk_test_FakeKey123456789ABCDEFGHIJKL"
                val customerId = "cus_123456789"
                val cardNumber = "4532123456789012"

                // Capture variables with security scanning
                val capturedVars = mapOf(
                    "amount" to amount,
                    "currency" to currency,
                    "stripeApiKey" to stripeApiKey,  // Will be detected as CRITICAL
                    "customerId" to customerId,
                    "cardNumber" to cardNumber,      // Will be detected as HIGH
                    "timestamp" to System.currentTimeMillis()
                )

                // This triggers auto-registration and eventual snapshot capture
                tracekitSDK.captureSnapshot("payment-processing", capturedVars)

                // Simulate payment processing
                simulateProcessing()

                logger.info("Payment processed successfully")

                span.setAttribute("payment.status", "success")
                span.setStatus(StatusCode.OK)

                ResponseEntity.ok(
                    mapOf(
                        "status" to "success",
                        "transactionId" to "txn_${System.currentTimeMillis()}",
                        "amount" to amount,
                        "currency" to currency,
                        "message" to "Payment processed successfully",
                        "note" to "Security scanning detected sensitive data - check dashboard for security events"
                    )
                )
            } catch (e: Exception) {
                logger.error("Payment processing failed", e)
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message)

                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    mapOf("error" to "Payment processing failed", "message" to e.message)
                )
            } finally {
                span.end()
            }
        }
    }

    private fun simulateProcessing() {
        Thread.sleep(Random.nextLong(50, 201))
    }

    companion object {
        private val USERS = mapOf(
            "1" to User("1", "Alice Johnson", "alice@example.com"),
            "2" to User("2", "Bob Smith", "bob@example.com"),
            "3" to User("3", "Charlie Brown", "charlie@example.com")
        )
    }

    data class User(
        val id: String,
        val name: String,
        val email: String
    )
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

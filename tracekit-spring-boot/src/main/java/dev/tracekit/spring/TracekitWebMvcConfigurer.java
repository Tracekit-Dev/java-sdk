package dev.tracekit.spring;

import dev.tracekit.TracekitSDK;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * Auto-configuration for TraceKit Spring MVC instrumentation.
 *
 * <p>This configuration automatically instruments HTTP requests to create SERVER spans
 * for all incoming requests. This enables automatic request tracking and DDoS detection
 * without requiring manual instrumentation.</p>
 *
 * <p>The interceptor captures:</p>
 * <ul>
 *   <li>HTTP method, route, and status code</li>
 *   <li>Client IP address</li>
 *   <li>User agent</li>
 *   <li>Request duration</li>
 * </ul>
 *
 * <p>To disable auto-instrumentation, set {@code tracekit.auto-instrument=false} in
 * application.properties.</p>
 */
@AutoConfiguration
@ConditionalOnClass({TracekitSDK.class, WebMvcConfigurer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "tracekit", name = "auto-instrument", havingValue = "true", matchIfMissing = true)
public class TracekitWebMvcConfigurer implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(TracekitWebMvcConfigurer.class);

    private final TracekitSDK tracekitSDK;
    private final Tracer tracer;

    public TracekitWebMvcConfigurer(TracekitSDK tracekitSDK) {
        this.tracekitSDK = tracekitSDK;
        this.tracer = tracekitSDK.getTracer("tracekit-spring-boot-auto");
        logger.info("✅ TraceKit auto-instrumentation enabled for HTTP requests");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TracekitHandlerInterceptor(tracer));
    }

    /**
     * HandlerInterceptor that creates SERVER spans for all HTTP requests.
     */
    private static class TracekitHandlerInterceptor implements HandlerInterceptor {

        private static final String SPAN_ATTRIBUTE = "tracekit.span";
        private static final String SCOPE_ATTRIBUTE = "tracekit.scope";
        private final Tracer tracer;

        public TracekitHandlerInterceptor(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            // Create SERVER span for this request
            String spanName = request.getMethod() + " " + getRoutePath(request);

            Span span = tracer.spanBuilder(spanName)
                    .setSpanKind(SpanKind.SERVER)
                    .startSpan();

            // Set common HTTP attributes
            span.setAttribute("http.method", request.getMethod());
            span.setAttribute("http.route", getRoutePath(request));
            span.setAttribute("http.target", request.getRequestURI());
            span.setAttribute("http.client_ip", HttpUtils.extractClientIp(request));

            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                span.setAttribute("http.user_agent", userAgent);
            }

            // Store span in request attributes for later access
            Scope scope = span.makeCurrent();
            request.setAttribute(SPAN_ATTRIBUTE, span);
            request.setAttribute(SCOPE_ATTRIBUTE, scope);

            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                     Object handler, Exception ex) {
            // Retrieve and complete the span
            Span span = (Span) request.getAttribute(SPAN_ATTRIBUTE);
            Scope scope = (Scope) request.getAttribute(SCOPE_ATTRIBUTE);

            if (span != null) {
                // Set HTTP status code
                span.setAttribute("http.status_code", response.getStatus());

                // Handle exceptions
                if (ex != null) {
                    span.recordException(ex);
                    span.setStatus(StatusCode.ERROR, ex.getMessage());
                } else if (response.getStatus() >= 400) {
                    span.setStatus(StatusCode.ERROR);
                } else {
                    span.setStatus(StatusCode.OK);
                }

                // Close scope first, then end span
                if (scope != null) {
                    scope.close();
                }
                span.end();
            }
        }

        /**
         * Extract route path from request, handling path parameters.
         */
        private String getRoutePath(HttpServletRequest request) {
            String requestURI = request.getRequestURI();

            // Try to get the pattern from Spring MVC (if available)
            Object pattern = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
            if (pattern != null) {
                return pattern.toString();
            }

            // Fallback to raw URI
            return requestURI;
        }
    }
}

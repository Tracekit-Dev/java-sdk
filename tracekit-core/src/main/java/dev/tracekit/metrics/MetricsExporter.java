package dev.tracekit.metrics;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MetricsExporter sends metrics to the backend in OTLP format.
 */
public final class MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(MetricsExporter.class);
    private final String endpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;

    public MetricsExporter(String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
        this.gson = new Gson();
    }

    public void export(List<MetricDataPoint> dataPoints) throws IOException, InterruptedException {
        if (dataPoints.isEmpty()) return;

        Map<String, Object> payload = toOTLP(dataPoints);
        String jsonBody = gson.toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("X-API-Key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
    }

    private Map<String, Object> toOTLP(List<MetricDataPoint> dataPoints) {
        // Group by name and type
        Map<String, List<MetricDataPoint>> grouped = dataPoints.stream()
            .collect(Collectors.groupingBy(dp -> dp.getName() + ":" + dp.getType()));

        List<Map<String, Object>> metrics = new ArrayList<>();

        for (Map.Entry<String, List<MetricDataPoint>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String name = parts[0];
            String type = parts[1];

            List<Map<String, Object>> otlpDataPoints = entry.getValue().stream()
                .map(this::convertDataPoint)
                .collect(Collectors.toList());

            Map<String, Object> metric = new HashMap<>();
            metric.put("name", name);

            if ("counter".equals(type)) {
                Map<String, Object> sum = new HashMap<>();
                sum.put("dataPoints", otlpDataPoints);
                sum.put("aggregationTemporality", 2); // DELTA
                sum.put("isMonotonic", true);
                metric.put("sum", sum);
            } else {
                Map<String, Object> gauge = new HashMap<>();
                gauge.put("dataPoints", otlpDataPoints);
                metric.put("gauge", gauge);
            }

            metrics.add(metric);
        }

        Map<String, Object> scope = new HashMap<>();
        scope.put("name", "tracekit");

        Map<String, Object> scopeMetric = new HashMap<>();
        scopeMetric.put("scope", scope);
        scopeMetric.put("metrics", metrics);

        Map<String, Object> resourceMetric = new HashMap<>();
        resourceMetric.put("scopeMetrics", Collections.singletonList(scopeMetric));

        Map<String, Object> result = new HashMap<>();
        result.put("resourceMetrics", Collections.singletonList(resourceMetric));

        return result;
    }

    private Map<String, Object> convertDataPoint(MetricDataPoint dp) {
        List<Map<String, Object>> attributes = dp.getTags().entrySet().stream()
            .map(e -> {
                Map<String, Object> attr = new HashMap<>();
                attr.put("key", e.getKey());
                Map<String, Object> value = new HashMap<>();
                value.put("stringValue", e.getValue());
                attr.put("value", value);
                return attr;
            })
            .collect(Collectors.toList());

        Map<String, Object> dataPoint = new HashMap<>();
        dataPoint.put("attributes", attributes);
        dataPoint.put("timeUnixNano", dp.getTimestampNanos());  // Send as number, not string
        dataPoint.put("asDouble", dp.getValue());

        return dataPoint;
    }
}

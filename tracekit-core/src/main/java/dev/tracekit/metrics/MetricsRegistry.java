package dev.tracekit.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MetricsRegistry manages all metrics and their lifecycle.
 */
public final class MetricsRegistry {
    private final Map<String, CounterImpl> counters = new ConcurrentHashMap<>();
    private final Map<String, GaugeImpl> gauges = new ConcurrentHashMap<>();
    private final Map<String, HistogramImpl> histograms = new ConcurrentHashMap<>();
    private final MetricsBuffer buffer;

    public MetricsRegistry(String endpoint, String apiKey) {
        this.buffer = new MetricsBuffer(endpoint, apiKey);
        this.buffer.start();
    }

    public Counter counter(String name, Map<String, String> tags) {
        String key = metricKey(name, tags);
        return counters.computeIfAbsent(key, k -> new CounterImpl(name, tags, buffer));
    }

    public Gauge gauge(String name, Map<String, String> tags) {
        String key = metricKey(name, tags);
        return gauges.computeIfAbsent(key, k -> new GaugeImpl(name, tags, buffer));
    }

    public Histogram histogram(String name, Map<String, String> tags) {
        String key = metricKey(name, tags);
        return histograms.computeIfAbsent(key, k -> new HistogramImpl(name, tags, buffer));
    }

    public void shutdown() {
        buffer.shutdown();
    }

    private String metricKey(String name, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return name;
        }

        StringBuilder sb = new StringBuilder(name).append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (!first) sb.append(',');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    // Implementation classes
    private static class CounterImpl implements Counter {
        private final String name;
        private final Map<String, String> tags;
        private final MetricsBuffer buffer;

        CounterImpl(String name, Map<String, String> tags, MetricsBuffer buffer) {
            this.name = name;
            this.tags = tags != null ? new HashMap<>(tags) : Collections.emptyMap();
            this.buffer = buffer;
        }

        @Override
        public void inc() {
            add(1.0);
        }

        @Override
        public void add(double value) {
            if (value < 0) return;
            buffer.add(new MetricDataPoint(name, tags, value, System.currentTimeMillis() * 1_000_000, "counter"));
        }
    }

    private static class GaugeImpl implements Gauge {
        private final String name;
        private final Map<String, String> tags;
        private final MetricsBuffer buffer;
        private volatile double value = 0.0;

        GaugeImpl(String name, Map<String, String> tags, MetricsBuffer buffer) {
            this.name = name;
            this.tags = tags != null ? new HashMap<>(tags) : Collections.emptyMap();
            this.buffer = buffer;
        }

        @Override
        public void set(double value) {
            this.value = value;
            buffer.add(new MetricDataPoint(name, tags, value, System.currentTimeMillis() * 1_000_000, "gauge"));
        }

        @Override
        public void inc() {
            this.value += 1.0;
            buffer.add(new MetricDataPoint(name, tags, this.value, System.currentTimeMillis() * 1_000_000, "gauge"));
        }

        @Override
        public void dec() {
            this.value -= 1.0;
            buffer.add(new MetricDataPoint(name, tags, this.value, System.currentTimeMillis() * 1_000_000, "gauge"));
        }
    }

    private static class HistogramImpl implements Histogram {
        private final String name;
        private final Map<String, String> tags;
        private final MetricsBuffer buffer;

        HistogramImpl(String name, Map<String, String> tags, MetricsBuffer buffer) {
            this.name = name;
            this.tags = tags != null ? new HashMap<>(tags) : Collections.emptyMap();
            this.buffer = buffer;
        }

        @Override
        public void record(double value) {
            buffer.add(new MetricDataPoint(name, tags, value, System.currentTimeMillis() * 1_000_000, "histogram"));
        }
    }
}

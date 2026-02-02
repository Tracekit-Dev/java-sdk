package dev.tracekit.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a single metric data point.
 */
public final class MetricDataPoint {
    private final String name;
    private final Map<String, String> tags;
    private final double value;
    private final long timestampNanos;
    private final String type;

    public MetricDataPoint(String name, Map<String, String> tags, double value, long timestampNanos, String type) {
        this.name = name;
        this.tags = Collections.unmodifiableMap(tags);
        this.value = value;
        this.timestampNanos = timestampNanos;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public double getValue() {
        return value;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    public String getType() {
        return type;
    }
}

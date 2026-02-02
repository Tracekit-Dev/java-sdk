package dev.tracekit.metrics;

/**
 * Histogram tracks value distributions.
 */
public interface Histogram {
    /**
     * Record a value.
     *
     * @param value the value to record
     */
    void record(double value);
}

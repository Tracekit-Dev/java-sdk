package dev.tracekit.metrics;

/**
 * Gauge tracks point-in-time values.
 */
public interface Gauge {
    /**
     * Set the gauge to the given value.
     *
     * @param value the new value
     */
    void set(double value);

    /**
     * Increment the gauge by 1.
     */
    void inc();

    /**
     * Decrement the gauge by 1.
     */
    void dec();
}

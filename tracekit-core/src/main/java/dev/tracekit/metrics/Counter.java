package dev.tracekit.metrics;

/**
 * Counter tracks monotonically increasing values.
 */
public interface Counter {
    /**
     * Increment the counter by 1.
     */
    void inc();

    /**
     * Add the given value to the counter.
     *
     * @param value the value to add (must be non-negative)
     */
    void add(double value);
}

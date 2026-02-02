package dev.tracekit.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MetricsBuffer collects metrics and flushes them periodically.
 */
public final class MetricsBuffer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsBuffer.class);
    private static final int MAX_SIZE = 100;
    private static final long FLUSH_INTERVAL_SECONDS = 10;

    private final List<MetricDataPoint> data = new ArrayList<>();
    private final MetricsExporter exporter;
    private final ScheduledExecutorService scheduler;
    private volatile boolean isShutdown = false;

    public MetricsBuffer(String endpoint, String apiKey, String serviceName) {
        this.exporter = new MetricsExporter(endpoint, apiKey, serviceName);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tracekit-metrics-flush");
            t.setDaemon(true);
            return t;
        });
        logger.info("📊 MetricsBuffer created with endpoint: {} for service: {}", endpoint, serviceName);
    }

    public void start() {
        logger.info("📊 MetricsBuffer scheduler starting - will flush every {} seconds", FLUSH_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(
            this::flush,
            FLUSH_INTERVAL_SECONDS,
            FLUSH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    public synchronized void add(MetricDataPoint dataPoint) {
        if (isShutdown) return;

        data.add(dataPoint);

        if (data.size() >= MAX_SIZE) {
            flush();
        }
    }

    private synchronized void flush() {
        if (data.isEmpty()) {
            logger.debug("📊 Metrics flush called but buffer is empty");
            return;
        }

        logger.info("📊 Flushing {} metrics to backend", data.size());
        List<MetricDataPoint> toExport = new ArrayList<>(data);
        data.clear();

        try {
            exporter.export(toExport);
            logger.info("📊 Successfully exported {} metrics", toExport.size());
        } catch (Exception e) {
            logger.error("Failed to export metrics", e);
        }
    }

    public void shutdown() {
        isShutdown = true;
        flush();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

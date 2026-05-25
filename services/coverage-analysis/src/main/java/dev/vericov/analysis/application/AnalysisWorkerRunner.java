package dev.vericov.analysis.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class AnalysisWorkerRunner {
    private static final Logger LOGGER = Logger.getLogger(AnalysisWorkerRunner.class.getName());

    private final AnalysisWorker worker;
    private final boolean enabled;
    private final Duration idleDelay;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    @Inject
    public AnalysisWorkerRunner(AnalysisWorker worker) {
        this(
                worker,
                workerEnabled(),
                Duration.ofMillis(longEnv("VERICOV_ANALYSIS_WORKER_IDLE_DELAY_MS", 1000L)));
    }

    AnalysisWorkerRunner(AnalysisWorker worker, boolean enabled, Duration idleDelay) {
        this.worker = worker;
        this.enabled = enabled;
        this.idleDelay = idleDelay;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            LOGGER.info("Coverage analysis worker is disabled");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = Thread.ofVirtual()
                .name("coverage-analysis-worker")
                .start(this::runLoop);
        LOGGER.info("Coverage analysis worker started");
    }

    void onApplicationInitialized(@Observes @Initialized(ApplicationScoped.class) Object event) {
        start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop() {
        while (running.get()) {
            try {
                int handled = worker.pollOnce();
                if (handled == 0) {
                    Thread.sleep(idleDelay);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Coverage analysis worker poll failed", exception);
                sleepAfterFailure();
            }
        }
    }

    private void sleepAfterFailure() {
        try {
            Thread.sleep(idleDelay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private static boolean workerEnabled() {
        String explicit = System.getenv("VERICOV_ANALYSIS_WORKER_ENABLED");
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit);
        }
        String jdbcUrl = System.getenv("VERICOV_ANALYSIS_DB_URL");
        return jdbcUrl != null && !jdbcUrl.isBlank();
    }

    private static long longEnv(String name, long fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Long.parseLong(value);
    }
}

package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisWorkerRunnerTest {

    @Test
    void startsWorkerLoopOnlyOnceWhenStartupIsTriggeredTwice() throws InterruptedException {
        BlockingWorker worker = new BlockingWorker();
        AnalysisWorkerRunner runner = new AnalysisWorkerRunner(worker, true, Duration.ofMillis(1));

        runner.start();
        assertTrue(worker.firstPollStarted.await(1, TimeUnit.SECONDS));

        runner.start();
        Thread.sleep(50);

        assertEquals(1, worker.pollCalls.get());
        worker.releasePoll.countDown();
        runner.stop();
    }

    private static final class BlockingWorker extends AnalysisWorker {
        private final AtomicInteger pollCalls = new AtomicInteger();
        private final CountDownLatch firstPollStarted = new CountDownLatch(1);
        private final CountDownLatch releasePoll = new CountDownLatch(1);

        private BlockingWorker() {
            super(new NoopQueue(), message -> {
            }, "coverage_analysis_jobs", 120, 10);
        }

        @Override
        public int pollOnce() {
            pollCalls.incrementAndGet();
            firstPollStarted.countDown();
            try {
                releasePoll.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }
    }

    private static final class NoopQueue implements AnalysisJobQueue {
        @Override
        public List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize) {
            return List.of();
        }

        @Override
        public void archive(String queueName, long messageId) {
        }

        @Override
        public void reschedule(String queueName, long messageId, int delaySeconds) {
        }

        @Override
        public void moveToDeadLetter(
                String sourceQueueName,
                String deadLetterQueueName,
                QueuedAnalysisMessage message,
                String reason) {
        }
    }
}

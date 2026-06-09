package dev.vericov.analysis.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcAnalysisJobQueueTest {
    @Test
    void readsAndMutatesQueueMessagesThroughPgmq() {
        JdbcProxySupport.RecordingDataSource dataSource = JdbcProxySupport.dataSource()
                .whenSqlContains(
                        "select msg_id, read_ct, message::text from pgmq.read",
                        new JdbcProxySupport.StatementBehavior().withRows(List.of(JdbcProxySupport.row(
                                "msg_id",
                                101L,
                                "read_ct",
                                2,
                                "message",
                                """
                                {"schema_version":1,"event_type":"upload.received","upload_id":"03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6","analysis_job_id":"fb0e1e5d-55d7-4f74-9303-7a93400d53a1","tenant_id":"0f4f478a-3fc0-45c4-b274-43a0e18850cf","repository_id":"4d607f16-1af7-4d3b-ac38-06454cba463c","commit_sha":"abc123"}
                                """))))
                .whenSqlContains("select pgmq.archive", new JdbcProxySupport.StatementBehavior())
                .whenSqlContains("select * from pgmq.set_vt", new JdbcProxySupport.StatementBehavior())
                .whenSqlContains("select * from pgmq.send", new JdbcProxySupport.StatementBehavior());
        JdbcAnalysisJobQueue queue = new JdbcAnalysisJobQueue(dataSource, new AnalysisMessageJsonCodec());

        List<QueuedAnalysisMessage> messages = queue.read("coverage_analysis_jobs", 300, 10);
        queue.archive("coverage_analysis_jobs", 101L);
        queue.reschedule("coverage_analysis_jobs", 101L, 30);
        queue.moveToDeadLetter("coverage_analysis_jobs", "coverage_analysis_dead_letters", messages.getFirst(), "boom");

        assertEquals(1, messages.size());
        assertEquals(101L, messages.getFirst().messageId());
        assertEquals("upload.received", messages.getFirst().event().eventType());
        assertTrue(dataSource.preparedSql().stream().anyMatch(sql -> sql.contains("pgmq.read")));
        assertTrue(dataSource.preparedSql().stream().anyMatch(sql -> sql.contains("pgmq.archive")));
        assertTrue(dataSource.preparedSql().stream().anyMatch(sql -> sql.contains("pgmq.set_vt")));
        assertTrue(dataSource.preparedSql().stream().anyMatch(sql -> sql.contains("pgmq.send")));
    }

    @Test
    void wrapsSqlFailuresWithOperationSpecificMessages() {
        JdbcAnalysisJobQueue readQueue = new JdbcAnalysisJobQueue(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "pgmq.read",
                        new JdbcProxySupport.StatementBehavior()
                                .withExecuteQueryException(new SQLException("read failed"))),
                new AnalysisMessageJsonCodec());
        IllegalStateException readFailure = assertThrows(
                IllegalStateException.class,
                () -> readQueue.read("coverage_analysis_jobs", 300, 10));
        assertTrue(readFailure.getMessage().contains("Failed to read analysis queue coverage_analysis_jobs"));
        assertInstanceOf(SQLException.class, readFailure.getCause());

        JdbcAnalysisJobQueue archiveQueue = new JdbcAnalysisJobQueue(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "pgmq.archive",
                        new JdbcProxySupport.StatementBehavior()
                                .withExecuteException(new SQLException("archive failed"))),
                new AnalysisMessageJsonCodec());
        IllegalStateException archiveFailure = assertThrows(
                IllegalStateException.class,
                () -> archiveQueue.archive("coverage_analysis_jobs", 7L));
        assertTrue(archiveFailure.getMessage().contains("Failed to update analysis queue message 7"));

        JdbcAnalysisJobQueue rescheduleQueue = new JdbcAnalysisJobQueue(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "pgmq.set_vt",
                        new JdbcProxySupport.StatementBehavior()
                                .withExecuteException(new SQLException("reschedule failed"))),
                new AnalysisMessageJsonCodec());
        IllegalStateException rescheduleFailure = assertThrows(
                IllegalStateException.class,
                () -> rescheduleQueue.reschedule("coverage_analysis_jobs", 8L, 30));
        assertTrue(rescheduleFailure.getMessage().contains("Failed to reschedule analysis queue message 8"));

        QueuedAnalysisMessage message = new QueuedAnalysisMessage(
                9L,
                1,
                new dev.vericov.analysis.domain.UploadReceivedEvent(
                        1,
                        "upload.received",
                        UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6"),
                        UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1"),
                        UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf"),
                        UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"),
                        "abc123"));
        JdbcAnalysisJobQueue deadLetterQueue = new JdbcAnalysisJobQueue(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "pgmq.send",
                        new JdbcProxySupport.StatementBehavior()
                                .withExecuteException(new SQLException("dead letter failed"))),
                new AnalysisMessageJsonCodec());
        IllegalStateException deadLetterFailure = assertThrows(
                IllegalStateException.class,
                () -> deadLetterQueue.moveToDeadLetter(
                        "coverage_analysis_jobs",
                        "coverage_analysis_dead_letters",
                        message,
                        "bad payload"));
        assertTrue(deadLetterFailure.getMessage().contains("Failed to move analysis queue message 9 to dead letter queue"));
    }
}

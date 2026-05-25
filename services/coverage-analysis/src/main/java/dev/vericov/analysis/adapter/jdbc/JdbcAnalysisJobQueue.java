package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public class JdbcAnalysisJobQueue implements AnalysisJobQueue {
    private final DataSource dataSource;
    private final AnalysisMessageJsonCodec codec;

    public JdbcAnalysisJobQueue(DataSource dataSource, AnalysisMessageJsonCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize) {
        String sql = "select msg_id, read_ct, message::text from pgmq.read(?, ?, ?)";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, queueName);
            statement.setInt(2, visibilityTimeoutSeconds);
            statement.setInt(3, batchSize);
            try (var resultSet = statement.executeQuery()) {
                List<QueuedAnalysisMessage> messages = new ArrayList<>();
                while (resultSet.next()) {
                    messages.add(codec.fromPgmqRecord(
                            resultSet.getLong("msg_id"),
                            resultSet.getInt("read_ct"),
                            resultSet.getString("message")));
                }
                return List.copyOf(messages);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read analysis queue " + queueName, exception);
        }
    }

    @Override
    public void archive(String queueName, long messageId) {
        executeQueueMessageStatement("select pgmq.archive(?, ?)", queueName, messageId);
    }

    @Override
    public void reschedule(String queueName, long messageId, int delaySeconds) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("select * from pgmq.set_vt(?, ?, ?)")) {
            statement.setString(1, queueName);
            statement.setLong(2, messageId);
            statement.setInt(3, delaySeconds);
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to reschedule analysis queue message " + messageId, exception);
        }
    }

    @Override
    public void moveToDeadLetter(
            String sourceQueueName,
            String deadLetterQueueName,
            QueuedAnalysisMessage message,
            String reason) {
        String payload = codec.toDeadLetterJson(message, reason);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("select * from pgmq.send(?, ?::jsonb, 0)")) {
            statement.setString(1, deadLetterQueueName);
            statement.setString(2, payload);
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to move analysis queue message " + message.messageId() + " to dead letter queue",
                    exception);
        }
    }

    private void executeQueueMessageStatement(String sql, String queueName, long messageId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, queueName);
            statement.setLong(2, messageId);
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update analysis queue message " + messageId, exception);
        }
    }
}

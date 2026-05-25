package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisMessageJsonCodecTest {

    @Test
    void parsesUploadReceivedMessageFromPgmqJson() {
        AnalysisMessageJsonCodec codec = new AnalysisMessageJsonCodec();
        String payload = """
                {
                  "schema_version": 1,
                  "event_type": "upload.received",
                  "upload_id": "03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6",
                  "analysis_job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1",
                  "tenant_id": "0f4f478a-3fc0-45c4-b274-43a0e18850cf",
                  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
                  "commit_sha": "abc123"
                }
                """;

        QueuedAnalysisMessage message = codec.fromPgmqRecord(55L, 2, payload);

        assertEquals(55L, message.messageId());
        assertEquals(2, message.readCount());
        assertEquals(1, message.event().schemaVersion());
        assertEquals("upload.received", message.event().eventType());
        assertEquals(UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6"), message.event().uploadId());
        assertEquals(UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1"), message.event().analysisJobId());
        assertEquals(UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf"), message.event().tenantId());
        assertEquals(UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"), message.event().repositoryId());
        assertEquals("abc123", message.event().commitSha());
    }

    @Test
    void writesDeadLetterPayloadWithReasonAndOriginalMessage() {
        AnalysisMessageJsonCodec codec = new AnalysisMessageJsonCodec();
        String deadLetter = codec.toDeadLetterJson(
                new QueuedAnalysisMessage(
                        55L,
                        2,
                        new dev.vericov.analysis.domain.UploadReceivedEvent(
                                1,
                                "upload.received",
                                UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6"),
                                UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1"),
                                UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf"),
                                UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"),
                                "abc123")),
                "invalid coverage artifact");

        assertEquals("""
                {"reason":"invalid coverage artifact","source_message_id":55,"source_read_count":2,"message":{"schema_version":1,"event_type":"upload.received","upload_id":"03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6","analysis_job_id":"fb0e1e5d-55d7-4f74-9303-7a93400d53a1","tenant_id":"0f4f478a-3fc0-45c4-b274-43a0e18850cf","repository_id":"4d607f16-1af7-4d3b-ac38-06454cba463c","commit_sha":"abc123"}}""",
                deadLetter);
    }
}

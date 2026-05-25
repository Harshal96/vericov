package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureXmlCoverageDocumentReaderTest {
    @Test
    void parsesOrdinaryCoverageXml() {
        SecureXmlCoverageDocumentReader reader = new SecureXmlCoverageDocumentReader();

        String root = reader.read(
                        "coverage.xml",
                        "<coverage><packages /></coverage>".getBytes(StandardCharsets.UTF_8))
                .getDocumentElement()
                .getTagName();

        assertEquals("coverage", root);
    }

    @Test
    void rejectsDoctypeDeclarations() {
        SecureXmlCoverageDocumentReader reader = new SecureXmlCoverageDocumentReader();
        byte[] malicious = """
                <!DOCTYPE coverage [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <coverage>&xxe;</coverage>
                """.getBytes(StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reader.read("coverage.xml", malicious));

        assertEquals("Invalid XML coverage artifact coverage.xml", exception.getMessage());
    }
}

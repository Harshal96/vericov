package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LcovCoverageParserTest {

    @Test
    void parsesLineBranchAndFunctionCoverage() {
        LcovCoverageParser parser = new LcovCoverageParser();
        byte[] content = """
                TN:
                SF:src/main/java/App.java
                FN:10,main
                FNDA:1,main
                FN:20,unused
                FNDA:0,unused
                DA:10,1
                DA:11,0
                DA:12,3
                BRDA:10,0,0,1
                BRDA:10,0,1,-
                BRDA:12,1,0,0
                end_of_record
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse("lcov.info", content);

        assertEquals(1, parsed.files().size());
        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/main/java/App.java", file.filePath());
        assertEquals(3, file.line().total());
        assertEquals(2, file.line().covered());
        assertEquals(3, file.branch().total());
        assertEquals(1, file.branch().covered());
        assertEquals(2, file.function().total());
        assertEquals(1, file.function().covered());
        assertEquals(3, file.statement().total());
        assertEquals(2, file.statement().covered());
    }

    @Test
    void preservesLineHitCountsFromDaRecords() {
        LcovCoverageParser parser = new LcovCoverageParser();
        ParsedCoverage parsed = parser.parse("coverage.lcov", """
                TN:
                SF:src/App.java
                DA:10,3
                DA:11,0
                end_of_record
                """.getBytes(StandardCharsets.UTF_8));

        ParsedCoverageFile file = parsed.files().getFirst();

        assertEquals(Map.of(10, 3L, 11, 0L), file.lineHits());
        assertEquals(Set.of(10, 11), file.executableLines());
        assertEquals(Set.of(10), file.coveredLines());
        assertEquals(2, file.line().total());
        assertEquals(1, file.line().covered());
    }

    @Test
    void mergesDuplicateFileRecordsWithoutDoubleCountingLines() {
        LcovCoverageParser parser = new LcovCoverageParser();
        byte[] content = """
                TN:
                SF:src/main/java/App.java
                DA:10,0
                DA:11,1
                end_of_record
                TN:
                SF:src/main/java/App.java
                DA:10,4
                DA:12,1
                end_of_record
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse("shard.lcov", content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals(3, file.line().total());
        assertEquals(3, file.line().covered());
    }
}

package dev.vericov.upload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vericov.upload.application.PatchCoverageDetails;
import dev.vericov.upload.application.PatchCoverageFileDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatchCoverageHttpResponseTest {
    @Test
    void mapsPatchCoverageDetailsToHttpResponse() {
        PatchCoverageDetails patch = new PatchCoverageDetails(
                "complete",
                "base-sha",
                "head-sha",
                8,
                10,
                1,
                2,
                List.of(new PatchCoverageFileDetails(
                        "src/Main.java",
                        "src/Old.java",
                        "renamed",
                        8,
                        10,
                        1,
                        2)));

        PatchCoverageHttpResponse response = PatchCoverageHttpResponse.from(patch);

        assertEquals("complete", response.status());
        assertEquals("base-sha", response.baseSha());
        assertEquals("head-sha", response.headSha());
        assertEquals(8, response.lineCovered());
        assertEquals(10, response.lineTotal());
        assertEquals(1, response.newlyMissedLineCount());
        assertEquals(2, response.lostCoverageLineCount());
        assertEquals(1, response.files().size());

        PatchCoverageFileHttpResponse file = response.files().get(0);
        assertEquals("src/Main.java", file.filePath());
        assertEquals("src/Old.java", file.oldFilePath());
        assertEquals("renamed", file.changeStatus());
        assertEquals(8, file.lineCovered());
        assertEquals(10, file.lineTotal());
        assertEquals(1, file.newlyMissedLineCount());
        assertEquals(2, file.lostCoverageLineCount());
    }

    @Test
    void returnsNullWhenNoPatchCoverageExists() {
        assertNull(PatchCoverageHttpResponse.from(null));
    }
}

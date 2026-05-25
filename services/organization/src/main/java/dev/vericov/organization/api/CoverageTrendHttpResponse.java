package dev.vericov.organization.api;

import dev.vericov.organization.application.CoverageTrendDetails;
import dev.vericov.organization.application.CoverageTrendPointDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CoverageTrendHttpResponse(
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String branch,
        String metric,
        List<Point> points) {

    public static CoverageTrendHttpResponse from(CoverageTrendDetails details) {
        return new CoverageTrendHttpResponse(
                details.organizationId(),
                details.repositoryId(),
                details.branch(),
                details.metric(),
                details.points().stream().map(Point::from).toList());
    }

    public record Point(
            @JsonbProperty("report_id")
            UUID reportId,
            @JsonbProperty("commit_sha")
            String commitSha,
            String branch,
            String metric,
            BigDecimal percent,
            @JsonbProperty("created_at")
            Instant createdAt) {

        static Point from(CoverageTrendPointDetails details) {
            return new Point(
                    details.reportId(),
                    details.commitSha(),
                    details.branch(),
                    details.metric(),
                    details.percent(),
                    details.createdAt());
        }
    }
}

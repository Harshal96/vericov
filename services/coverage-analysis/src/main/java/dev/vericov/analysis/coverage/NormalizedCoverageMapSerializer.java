package dev.vericov.analysis.coverage;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class NormalizedCoverageMapSerializer {
    private static final int SCHEMA_VERSION = 1;

    public byte[] serialize(CoverageReport report) {
        Objects.requireNonNull(report, "report");
        byte[] json = json(report).getBytes(StandardCharsets.UTF_8);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(json);
            gzip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize normalized coverage map", exception);
        }
    }

    private static String json(CoverageReport report) {
        return Json.createObjectBuilder()
                .add("schema_version", SCHEMA_VERSION)
                .add("report", reportMetadata(report))
                .add("totals", metrics(
                        report.line(),
                        report.branch(),
                        report.function(),
                        report.statement()))
                .add("files", files(report))
                .build()
                .toString();
    }

    private static JsonObjectBuilder reportMetadata(CoverageReport report) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("id", report.reportId().toString())
                .add("upload_id", report.uploadId().toString())
                .add("tenant_id", report.tenantId().toString())
                .add("repository_id", report.repositoryId().toString())
                .add("commit_sha", report.commitSha())
                .add("branch", report.branchName())
                .add("generated_at", report.generatedAt().toString());
        if (report.pullRequestNumber() == null) {
            builder.addNull("pull_request_number");
        } else {
            builder.add("pull_request_number", report.pullRequestNumber());
        }
        return builder;
    }

    private static JsonObjectBuilder metrics(
            CoverageMetric line,
            CoverageMetric branch,
            CoverageMetric function,
            CoverageMetric statement) {
        return Json.createObjectBuilder()
                .add("line", metric(line))
                .add("branch", metric(branch))
                .add("function", metric(function))
                .add("statement", metric(statement));
    }

    private static JsonObjectBuilder metric(CoverageMetric metric) {
        return Json.createObjectBuilder()
                .add("covered", metric.covered())
                .add("total", metric.total());
    }

    private static JsonArrayBuilder files(CoverageReport report) {
        Map<String, List<CoverageLineHit>> lineHitsByFile = report.lineHits().stream()
                .collect(Collectors.groupingBy(CoverageLineHit::filePath));
        JsonArrayBuilder builder = Json.createArrayBuilder();
        report.files().stream()
                .sorted(Comparator.comparing(CoverageFileSummary::filePath))
                .forEach(file -> builder.add(file(file, lineHitsByFile.getOrDefault(file.filePath(), List.of()))));
        return builder;
    }

    private static JsonObjectBuilder file(CoverageFileSummary file, List<CoverageLineHit> lineHits) {
        return Json.createObjectBuilder()
                .add("path", file.filePath())
                .add("metrics", metrics(file.line(), file.branch(), file.function(), file.statement()))
                .add("line_hits", lineHits(lineHits));
    }

    private static JsonArrayBuilder lineHits(List<CoverageLineHit> lineHits) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        lineHits.stream()
                .sorted(Comparator.comparingInt(CoverageLineHit::lineNumber))
                .forEach(lineHit -> builder.add(Json.createObjectBuilder()
                        .add("line", lineHit.lineNumber())
                        .add("hits", lineHit.hits())));
        return builder;
    }
}

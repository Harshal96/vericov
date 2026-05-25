package dev.vericov.analysis.adapter.git;

import dev.vericov.analysis.application.port.PullRequestDiffClient;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffLineType;
import dev.vericov.analysis.diff.PullRequestDiff;
import dev.vericov.analysis.diff.PullRequestDiffFile;
import dev.vericov.analysis.diff.PullRequestDiffLine;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InternalGitDiffHttpClient implements PullRequestDiffClient {
    private final URI baseUri;
    private final String serviceToken;
    private final HttpClient httpClient;

    public InternalGitDiffHttpClient(URI baseUri, String serviceToken) {
        this(baseUri, serviceToken, HttpClient.newHttpClient());
    }

    InternalGitDiffHttpClient(URI baseUri, String serviceToken, HttpClient httpClient) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.serviceToken = Objects.requireNonNull(serviceToken, "serviceToken");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public PullRequestDiff fetch(CoverageAnalysisInput input, CoverageReport headReport) {
        if (input.organizationId() == null) {
            throw new IllegalStateException("organization_id is required for PR diff coverage");
        }
        URI uri = baseUri.resolve("/internal/v1/git/repositories/" + input.repositoryId()
                + "/pull-requests/" + input.pullRequestNumber()
                + "/diff?tenant_id=" + encode(input.tenantId().toString())
                + "&org_id=" + encode(input.organizationId().toString())
                + "&provider=" + encode(input.provider())
                + "&head_sha=" + encode(headReport.commitSha()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("X-Vericov-Service-Name", "coverage-analysis")
                .header("X-Vericov-Service-Token", serviceToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Git diff request failed with HTTP " + response.statusCode());
            }
            return readDiff(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Git diff request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git diff request interrupted", exception);
        }
    }

    private static PullRequestDiff readDiff(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body == null ? "{}" : body))) {
            JsonObject envelope = reader.readObject();
            JsonObject data = envelope.getJsonObject("data");
            if (data == null) {
                throw new IllegalStateException("Git diff response is missing data");
            }
            return new PullRequestDiff(
                    data.getString("base_sha"),
                    data.getString("head_sha"),
                    readFiles(data.getJsonArray("files")));
        }
    }

    private static List<PullRequestDiffFile> readFiles(JsonArray files) {
        if (files == null) {
            return List.of();
        }
        List<PullRequestDiffFile> values = new ArrayList<>();
        for (JsonValue value : files) {
            JsonObject file = value.asJsonObject();
            values.add(new PullRequestDiffFile(
                    file.getString("file_path"),
                    nullableString(file, "old_file_path"),
                    file.getString("change_status"),
                    readLines(file.getJsonArray("lines"))));
        }
        return List.copyOf(values);
    }

    private static List<PullRequestDiffLine> readLines(JsonArray lines) {
        if (lines == null) {
            return List.of();
        }
        List<PullRequestDiffLine> values = new ArrayList<>();
        for (JsonValue value : lines) {
            JsonObject line = value.asJsonObject();
            values.add(new PullRequestDiffLine(
                    nullableInteger(line, "base_line_number"),
                    nullableInteger(line, "head_line_number"),
                    DiffLineType.valueOf(line.getString("change_type").toUpperCase(java.util.Locale.ROOT))));
        }
        return List.copyOf(values);
    }

    private static String nullableString(JsonObject object, String name) {
        JsonValue value = object.get(name);
        return value instanceof JsonString string ? string.getString() : null;
    }

    private static Integer nullableInteger(JsonObject object, String name) {
        JsonValue value = object.get(name);
        if (value == null || value == JsonValue.NULL) {
            return null;
        }
        return object.getInt(name);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

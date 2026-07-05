package dev.vericov.upload.application;

import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only coverage query surface for agents and other automated
 * consumers. Every method requires the {@code uploads:read} scope; none can
 * upload, exchange runner tokens, or mutate any state.
 */
public class CoverageQueryService {
    private static final String READ_SCOPE = "uploads:read";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int MAX_PATH_LENGTH = 1024;

    private final RepositoryApiKeyAuthenticator authenticator;
    private final UploadRepository uploadRepository;

    public CoverageQueryService(RepositoryApiKeyAuthenticator authenticator, UploadRepository uploadRepository) {
        this.authenticator = authenticator;
        this.uploadRepository = uploadRepository;
    }

    public ResolvedCoverageRef resolveRef(String authorizationHeader, String ref) {
        RepositoryApiKeyPrincipal principal = authenticateRead(authorizationHeader);
        return uploadRepository.resolveCoverageRef(principal.repositoryId(), ref)
                .orElseThrow(() -> new InvalidUploadException(
                        "ref_not_found",
                        "No complete coverage report found for ref " + describeRef(ref)));
    }

    public CoverageSummary summary(String authorizationHeader, String ref) {
        ResolvedCoverageRef resolved = resolveRef(authorizationHeader, ref);
        CoverageReportDetails report = uploadRepository.coverageReportFor(resolved.uploadId())
                .orElseThrow(() -> new InvalidUploadException("ref_not_found", "Coverage report not found"));
        return new CoverageSummary(resolved, report);
    }

    public ComponentCoverageResult components(String authorizationHeader, String ref) {
        ResolvedCoverageRef resolved = resolveRef(authorizationHeader, ref);
        CoverageReportDetails report = uploadRepository.coverageReportFor(resolved.uploadId())
                .orElseThrow(() -> new InvalidUploadException("ref_not_found", "Coverage report not found"));
        return new ComponentCoverageResult(resolved, report.components());
    }

    public FilePage files(
            String authorizationHeader,
            String ref,
            String pathPrefix,
            String componentKey,
            String sort,
            Integer limit,
            String cursor) {
        ResolvedCoverageRef resolved = resolveRef(authorizationHeader, ref);
        if (pathPrefix != null && pathPrefix.length() > MAX_PATH_LENGTH) {
            throw new InvalidUploadException("validation_error", "path_prefix exceeds " + MAX_PATH_LENGTH + " characters");
        }
        if (sort != null && !sort.equals("path") && !sort.equals("line_percentage_asc")) {
            throw new InvalidUploadException("validation_error", "sort must be path or line_percentage_asc");
        }
        int effectiveLimit = boundedLimit(limit);
        int offset = decodeCursor(cursor);
        List<CoverageFileSummaryDetails> page = uploadRepository.filesFor(
                resolved.reportId(), pathPrefix, componentKey, sort, effectiveLimit + 1, offset);
        boolean truncated = page.size() > effectiveLimit;
        List<CoverageFileSummaryDetails> files = truncated ? page.subList(0, effectiveLimit) : page;
        String nextCursor = truncated ? encodeCursor(offset + effectiveLimit) : null;
        return new FilePage(resolved, files, truncated, nextCursor);
    }

    public FileDetailResult file(String authorizationHeader, String ref, String path) {
        ResolvedCoverageRef resolved = resolveRef(authorizationHeader, ref);
        if (path == null || path.isBlank()) {
            throw new InvalidUploadException("validation_error", "path is required");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new InvalidUploadException("validation_error", "path exceeds " + MAX_PATH_LENGTH + " characters");
        }
        Optional<CoverageFileDetail> detail = uploadRepository.fileDetail(resolved.reportId(), path);
        if (detail.isEmpty()) {
            String basename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            List<String> suggestions = uploadRepository.similarFilePaths(resolved.reportId(), basename, 5);
            throw new FileNotFoundQueryException(
                    "No file found at path " + path + " in the resolved report", suggestions);
        }
        return new FileDetailResult(resolved, detail.get());
    }

    public PatchCoverageResult patchForPullRequest(String authorizationHeader, int pullRequestNumber) {
        RepositoryApiKeyPrincipal principal = authenticateRead(authorizationHeader);
        PatchCoverageDetails patch = uploadRepository.patchForPullRequest(principal.repositoryId(), pullRequestNumber)
                .orElseThrow(() -> new InvalidUploadException(
                        "pull_request_not_found",
                        "No diff-bearing coverage report found for pull request " + pullRequestNumber
                                + ". Uploads must include a diff artifact."));
        return new PatchCoverageResult(patch);
    }

    public GapPage gaps(
            String authorizationHeader,
            String ref,
            String componentKey,
            String minRiskLevel,
            String status,
            Integer limit,
            String cursor) {
        ResolvedCoverageRef resolved = resolveRef(authorizationHeader, ref);
        int effectiveLimit = boundedLimit(limit);
        int offset = decodeCursor(cursor);
        List<CoverageGapFindingDetails> page = uploadRepository.gapsFor(
                resolved.reportId(), componentKey, minRiskLevel, status, effectiveLimit + 1, offset);
        boolean truncated = page.size() > effectiveLimit;
        List<CoverageGapFindingDetails> gaps = truncated ? page.subList(0, effectiveLimit) : page;
        String nextCursor = truncated ? encodeCursor(offset + effectiveLimit) : null;
        return new GapPage(resolved, gaps, truncated, nextCursor);
    }

    public GateResult gates(String authorizationHeader, String ref) {
        ResolvedCoverageRef resolved = resolveRef(authorizationHeader, ref);
        return new GateResult(resolved, uploadRepository.gatesFor(resolved.reportId()));
    }

    public CoverageGapManifest manifest(
            String authorizationHeader,
            String ref,
            Integer pullRequestNumber,
            String nextAction,
            String minRiskLevel,
            Integer limit) {
        RepositoryApiKeyPrincipal principal = authenticateRead(authorizationHeader);
        ResolvedCoverageRef resolved = pullRequestNumber != null
                ? uploadRepository.resolveCoverageRefForPullRequest(principal.repositoryId(), pullRequestNumber)
                        .orElseThrow(() -> new InvalidUploadException(
                                "pull_request_not_found",
                                "No diff-bearing coverage report found for pull request " + pullRequestNumber
                                        + ". Uploads must include a diff artifact."))
                : uploadRepository.resolveCoverageRef(principal.repositoryId(), ref)
                        .orElseThrow(() -> new InvalidUploadException(
                                "ref_not_found",
                                "No complete coverage report found for ref " + describeRef(ref)));
        CoverageReportDetails report = uploadRepository.coverageReportFor(resolved.uploadId())
                .orElseThrow(() -> new InvalidUploadException("ref_not_found", "Coverage report not found"));
        RepositoryInfo repositoryInfo = uploadRepository.repositoryInfo(principal.repositoryId())
                .orElse(new RepositoryInfo(null, null));
        PatchCoverageDetails patch = report.pullRequestNumber() == null
                ? null
                : uploadRepository.patchForPullRequest(principal.repositoryId(), report.pullRequestNumber())
                        .orElse(null);
        List<CoverageGateEvaluationDetails> failedGates = uploadRepository.gatesFor(resolved.reportId()).stream()
                .filter(gate -> "failed".equals(gate.status()) && gate.blocking())
                .toList();
        int effectiveLimit = boundedLimit(limit);
        List<CoverageGapManifestEntry> page = uploadRepository.gapManifestEntries(
                resolved.reportId(), nextAction, minRiskLevel, effectiveLimit + 1, 0);
        boolean truncated = page.size() > effectiveLimit;
        List<CoverageGapManifestEntry> entries = truncated ? page.subList(0, effectiveLimit) : page;
        return new CoverageGapManifest(
                1,
                Instant.now(),
                repositoryInfo,
                resolved,
                report.pullRequestNumber(),
                report.gateStatus(),
                report.configSha256(),
                patch,
                failedGates,
                entries,
                truncated);
    }

    private RepositoryApiKeyPrincipal authenticateRead(String authorizationHeader) {
        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess(authorizationHeader, null, null);
        if (!principal.hasScope(READ_SCOPE)) {
            throw new InvalidUploadException("scope_missing", "API key does not have " + READ_SCOPE + " scope");
        }
        return principal;
    }

    private static int boundedLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new InvalidUploadException("validation_error", "limit must be at least 1");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        if (cursor.length() > 512) {
            throw new InvalidUploadException("validation_error", "cursor exceeds 512 characters");
        }
        try {
            return Integer.parseInt(new String(
                    java.util.Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new InvalidUploadException("validation_error", "cursor is not valid");
        }
    }

    private static String encodeCursor(int offset) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String describeRef(String ref) {
        return ref == null || ref.isBlank() ? "<default branch>" : ref;
    }

    public record CoverageSummary(ResolvedCoverageRef resolved, CoverageReportDetails report) {
    }

    public record ComponentCoverageResult(ResolvedCoverageRef resolved, List<ComponentCoverageDetails> components) {
    }

    public record FilePage(
            ResolvedCoverageRef resolved,
            List<CoverageFileSummaryDetails> files,
            boolean truncated,
            String nextCursor) {
    }

    public record FileDetailResult(ResolvedCoverageRef resolved, CoverageFileDetail file) {
    }

    public record PatchCoverageResult(PatchCoverageDetails patch) {
    }

    public record GapPage(
            ResolvedCoverageRef resolved,
            List<CoverageGapFindingDetails> gaps,
            boolean truncated,
            String nextCursor) {
    }

    public record GateResult(ResolvedCoverageRef resolved, List<CoverageGateEvaluationDetails> gates) {
    }

    public static final class FileNotFoundQueryException extends InvalidUploadException {
        private final List<String> didYouMean;

        public FileNotFoundQueryException(String message, List<String> didYouMean) {
            super("file_not_found", message);
            this.didYouMean = List.copyOf(didYouMean == null ? List.of() : didYouMean);
        }

        public List<String> didYouMean() {
            return didYouMean;
        }
    }
}

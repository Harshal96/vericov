package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageWarningDetails;

public record CoverageWarningHttpResponse(
        String code,
        int count) {

    static CoverageWarningHttpResponse from(CoverageWarningDetails warning) {
        return new CoverageWarningHttpResponse(warning.code(), warning.count());
    }
}

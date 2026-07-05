package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageLineRange;

public record CoverageLineRangeHttpResponse(int start, int end) {
    public static CoverageLineRangeHttpResponse from(CoverageLineRange range) {
        return new CoverageLineRangeHttpResponse(range.start(), range.end());
    }
}

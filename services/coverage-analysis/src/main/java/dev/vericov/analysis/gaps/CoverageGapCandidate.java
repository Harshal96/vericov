package dev.vericov.analysis.gaps;

import dev.vericov.analysis.gates.RepositoryPackageNodeContext;
import java.util.List;
import java.util.Map;

record CoverageGapCandidate(
        String filePath,
        String targetType,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        String reasonCode,
        String componentKey,
        String componentName,
        String componentCriticality,
        List<String> owners,
        RepositoryPackageNodeContext packageNode,
        boolean debtSuppressed,
        boolean expiredDebtMatched,
        Map<String, Object> riskConfig) {

    CoverageGapCandidate {
        owners = List.copyOf(owners == null ? List.of() : owners);
        riskConfig = Map.copyOf(riskConfig == null ? Map.of() : riskConfig);
    }
}

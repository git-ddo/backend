package com.gitddo.analysis.contract;

import java.util.Map;

public record AiAnalysisError(
		String schemaVersion,
		String analysisId,
		AiErrorCode code,
		String message,
		boolean retryable,
		Map<String, Object> details
) {

	public AiAnalysisError {
		details = details == null ? Map.of() : Map.copyOf(details);
	}
}

package com.gitddo.analysis.presentation;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationStatus;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;

import java.time.Instant;
import java.util.UUID;

public record EvaluationResponse(
		UUID analysisId,
		EvaluationStatus status,
		P0EvidenceSnapshot evidenceSnapshot,
		AiAnalysisRequest aiRequest,
		AiAnalysisResponse report,
		String failureReason,
		Instant requestedAt,
		Instant startedAt,
		Instant completedAt
) {

	static EvaluationResponse from(EvaluationRun run) {
		return new EvaluationResponse(
				run.getAnalysisId(),
				run.getStatus(),
				run.getEvidenceSnapshot(),
				run.getAiRequest(),
				run.getResult(),
				run.getFailureReason(),
				run.getRequestedAt(),
				run.getStartedAt(),
				run.getCompletedAt()
		);
	}
}

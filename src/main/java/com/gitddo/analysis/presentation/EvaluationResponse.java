package com.gitddo.analysis.presentation;

import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationStatus;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;

import java.time.Instant;
import java.util.UUID;

public record EvaluationResponse(
		UUID analysisId,
		EvaluationStatus status,
		P0EvidenceSnapshot evidenceSnapshot,
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
				run.getFailureReason(),
				run.getRequestedAt(),
				run.getStartedAt(),
				run.getCompletedAt()
		);
	}
}

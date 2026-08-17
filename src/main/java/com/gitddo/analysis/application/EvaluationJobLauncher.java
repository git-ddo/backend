package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EvaluationJobLauncher {

	private static final int MAX_FAILURE_REASON_LENGTH = 1_000;

	private final EvaluationService evaluationService;
	private final P0EvidenceCollector p0EvidenceCollector;
	private final AiAnalysisRequestAssembler aiAnalysisRequestAssembler;

	public EvaluationJobLauncher(
			EvaluationService evaluationService,
			P0EvidenceCollector p0EvidenceCollector,
			AiAnalysisRequestAssembler aiAnalysisRequestAssembler
	) {
		this.evaluationService = evaluationService;
		this.p0EvidenceCollector = p0EvidenceCollector;
		this.aiAnalysisRequestAssembler = aiAnalysisRequestAssembler;
	}

	@Async("evaluationExecutor")
	public void launch(UUID analysisId, String githubAccessToken) {
		try {
			EvaluationInputSnapshot inputSnapshot =
					evaluationService.startCollection(analysisId);
			P0EvidenceSnapshot evidenceSnapshot =
					p0EvidenceCollector.collect(githubAccessToken, inputSnapshot);
			evaluationService.completeCollection(
					analysisId,
					evidenceSnapshot,
					aiAnalysisRequestAssembler.assemble(
							analysisId,
							inputSnapshot,
							evidenceSnapshot
					)
			);
		} catch (Exception exception) {
			evaluationService.fail(analysisId, safeFailureReason(exception));
		}
	}

	private String safeFailureReason(Exception exception) {
		String message = exception.getMessage();
		String reason = exception.getClass().getSimpleName()
				+ (message == null || message.isBlank() ? "" : ": " + message);
		return reason.length() <= MAX_FAILURE_REASON_LENGTH
				? reason
				: reason.substring(0, MAX_FAILURE_REASON_LENGTH);
	}
}

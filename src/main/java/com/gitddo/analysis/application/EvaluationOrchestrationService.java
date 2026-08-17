package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import org.springframework.stereotype.Service;

@Service
public class EvaluationOrchestrationService {

	private final EvaluationService evaluationService;
	private final EvaluationJobLauncher evaluationJobLauncher;

	public EvaluationOrchestrationService(
			EvaluationService evaluationService,
			EvaluationJobLauncher evaluationJobLauncher
	) {
		this.evaluationService = evaluationService;
		this.evaluationJobLauncher = evaluationJobLauncher;
	}

	public EvaluationRun request(
			Long githubUserId,
			Long portfolioId,
			String githubAccessToken
	) {
		EvaluationRun run = evaluationService.request(
				githubUserId,
				portfolioId,
				P0EvidenceSnapshot.CURRENT_EXTRACTOR_VERSION
		);
		evaluationJobLauncher.launch(run.getAnalysisId(), githubAccessToken);
		return run;
	}
}

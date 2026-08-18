package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationRunRepository;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.portfolio.application.PortfolioNotFoundException;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EvaluationService {

	private final PortfolioRepository portfolioRepository;
	private final EvaluationRunRepository evaluationRunRepository;
	private final EvaluationSnapshotFactory evaluationSnapshotFactory;

	public EvaluationService(
			PortfolioRepository portfolioRepository,
			EvaluationRunRepository evaluationRunRepository,
			EvaluationSnapshotFactory evaluationSnapshotFactory
	) {
		this.portfolioRepository = portfolioRepository;
		this.evaluationRunRepository = evaluationRunRepository;
		this.evaluationSnapshotFactory = evaluationSnapshotFactory;
	}

	@Transactional
	public EvaluationRun request(
			Long githubUserId,
			Long portfolioId,
			String evaluatorVersion
	) {
		Portfolio portfolio = portfolioRepository
				.findByIdAndOwnerGithubIdAndDeletedAtIsNull(portfolioId, githubUserId)
				.orElseThrow(PortfolioNotFoundException::new);
		if (portfolio.getRepositories().isEmpty()) {
			throw new PortfolioEvaluationNotReadyException();
		}
		EvaluationInputSnapshot snapshot = evaluationSnapshotFactory.create(portfolio);
		int sequence = evaluationRunRepository.findLatestSequence(portfolioId) + 1;

		return evaluationRunRepository.save(
				new EvaluationRun(portfolio, sequence, snapshot, evaluatorVersion)
		);
	}

	@Transactional
	public EvaluationInputSnapshot startCollection(UUID analysisId) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisId(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		run.startCollection();
		return run.getInputSnapshot();
	}

	@Transactional
	public void completeCollection(
			UUID analysisId,
			P0EvidenceSnapshot evidenceSnapshot,
			AiAnalysisRequest aiRequest
	) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisId(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		run.completeEvidenceCollection(evidenceSnapshot, aiRequest);
	}

	@Transactional
	public AiAnalysisRequest startAnalysis(UUID analysisId) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisId(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		run.startAnalysis();
		return run.getAiRequest();
	}

	@Transactional
	public void succeed(UUID analysisId, AiAnalysisResponse report) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisId(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		run.succeed(report);
	}

	@Transactional
	public void fail(UUID analysisId, String failureReason) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisId(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		run.fail(failureReason);
	}

	@Transactional(readOnly = true)
	public EvaluationRun get(
			Long githubUserId,
			Long portfolioId,
			UUID analysisId
	) {
		return evaluationRunRepository
				.findByAnalysisIdAndPortfolioIdAndPortfolioOwnerGithubId(
						analysisId,
						portfolioId,
						githubUserId
				)
				.orElseThrow(EvaluationNotFoundException::new);
	}
}

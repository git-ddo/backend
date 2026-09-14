package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationFailureCode;
import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationRunRepository;
import com.gitddo.analysis.domain.EvaluationStatus;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.portfolio.application.PortfolioNotFoundException;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
		return tryStartCollection(analysisId).orElseThrow(() ->
				new IllegalStateException("평가 상태가 REQUESTED일 때만 수행할 수 있습니다.")
		);
	}

	@Transactional
	public Optional<EvaluationInputSnapshot> tryStartCollection(UUID analysisId) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisIdForUpdate(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		if (run.getStatus() != EvaluationStatus.REQUESTED) {
			return Optional.empty();
		}
		run.startCollection();
		return Optional.of(run.getInputSnapshot());
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
	public void fail(UUID analysisId, EvaluationFailureCode failureCode, String failureReason) {
		EvaluationRun run = evaluationRunRepository.findByAnalysisId(analysisId)
				.orElseThrow(EvaluationNotFoundException::new);
		run.fail(failureCode, failureReason);
	}

	@Transactional
	public List<EvaluationRun> expireStale(
			Collection<EvaluationStatus> statuses,
			Instant deadline
	) {
		List<EvaluationRun> stale = evaluationRunRepository.findStaleInProgress(statuses, deadline);
		for (EvaluationRun run : stale) {
			run.fail(
					EvaluationFailureCode.EVALUATION_INTERRUPTED,
					"평가가 제한 시간 안에 끝나지 않아 중단했습니다."
			);
		}
		return stale;
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

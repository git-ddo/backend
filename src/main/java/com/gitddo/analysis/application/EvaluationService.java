package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationRunRepository;
import com.gitddo.portfolio.application.PortfolioNotFoundException;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}

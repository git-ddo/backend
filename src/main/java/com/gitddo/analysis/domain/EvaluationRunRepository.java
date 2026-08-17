package com.gitddo.analysis.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {

	@Query("""
			SELECT COALESCE(MAX(run.sequence), 0)
			FROM EvaluationRun run
			WHERE run.portfolio.id = :portfolioId
			""")
	int findLatestSequence(@Param("portfolioId") Long portfolioId);

	List<EvaluationRun> findByPortfolioIdOrderBySequenceDesc(Long portfolioId);

	Optional<EvaluationRun> findByAnalysisId(UUID analysisId);

	Optional<EvaluationRun> findByAnalysisIdAndPortfolioIdAndPortfolioOwnerGithubId(
			UUID analysisId,
			Long portfolioId,
			Long githubUserId
	);
}

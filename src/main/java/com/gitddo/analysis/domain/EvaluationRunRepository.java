package com.gitddo.analysis.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT run FROM EvaluationRun run WHERE run.analysisId = :analysisId")
	Optional<EvaluationRun> findByAnalysisIdForUpdate(@Param("analysisId") UUID analysisId);

	@Query("""
			SELECT run
			FROM EvaluationRun run
			WHERE run.status IN :statuses
			  AND COALESCE(run.startedAt, run.requestedAt) < :deadline
			""")
	List<EvaluationRun> findStaleInProgress(
			@Param("statuses") Collection<EvaluationStatus> statuses,
			@Param("deadline") Instant deadline
	);

	Optional<EvaluationRun> findByAnalysisIdAndPortfolioIdAndPortfolioOwnerGithubId(
			UUID analysisId,
			Long portfolioId,
			Long githubUserId
	);
}

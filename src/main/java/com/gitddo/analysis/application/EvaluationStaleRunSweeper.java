package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class EvaluationStaleRunSweeper {

	private static final Logger log = LoggerFactory.getLogger(EvaluationStaleRunSweeper.class);
	private static final Set<EvaluationStatus> IN_PROGRESS = EnumSet.of(
			EvaluationStatus.REQUESTED,
			EvaluationStatus.COLLECTING,
			EvaluationStatus.EVIDENCE_READY,
			EvaluationStatus.ANALYZING
	);

	private final EvaluationService evaluationService;
	private final Duration staleAfter;

	public EvaluationStaleRunSweeper(
			EvaluationService evaluationService,
			@Value("${gitddo.evaluation.stale-after:20m}") Duration staleAfter
	) {
		this.evaluationService = evaluationService;
		this.staleAfter = staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()
				? Duration.ofMinutes(20)
				: staleAfter;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void expireOnStartup() {
		expireStaleRuns();
	}

	@Scheduled(fixedDelayString = "${gitddo.evaluation.stale-check-interval:1m}")
	public void expireStaleRuns() {
		Instant deadline = Instant.now().minus(staleAfter);
		List<EvaluationRun> expired = evaluationService.expireStale(IN_PROGRESS, deadline);
		for (EvaluationRun run : expired) {
			log.warn(
					"evaluation interrupted analysisId={} statusWasStaleAfter={}",
					run.getAnalysisId(),
					staleAfter
			);
		}
	}
}

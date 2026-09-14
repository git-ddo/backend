package com.gitddo.analysis.application;

import com.gitddo.analysis.client.PortfolioReportClient;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.domain.EvaluationFailureCode;
import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EvaluationJobLauncher {

	private static final Logger log = LoggerFactory.getLogger(EvaluationJobLauncher.class);
	private static final int MAX_FAILURE_REASON_LENGTH = 1_000;

	private final EvaluationService evaluationService;
	private final P0EvidenceCollector p0EvidenceCollector;
	private final P1EvidenceCollector p1EvidenceCollector;
	private final P2EvidenceCollector p2EvidenceCollector;
	private final AiAnalysisRequestAssembler aiAnalysisRequestAssembler;
	private final PortfolioReportClient portfolioReportClient;
	private final AiAnalysisResponseValidator aiAnalysisResponseValidator;
	private final EvaluationFailureClassifier failureClassifier;
	private final AnalysisDepth maxAnalysisDepth;
	private final Set<UUID> runningAnalysisIds = ConcurrentHashMap.newKeySet();

	public EvaluationJobLauncher(
			EvaluationService evaluationService,
			P0EvidenceCollector p0EvidenceCollector,
			P1EvidenceCollector p1EvidenceCollector,
			P2EvidenceCollector p2EvidenceCollector,
			AiAnalysisRequestAssembler aiAnalysisRequestAssembler,
			PortfolioReportClient portfolioReportClient,
			AiAnalysisResponseValidator aiAnalysisResponseValidator,
			EvaluationFailureClassifier failureClassifier,
			@Value("${gitddo.ai.max-analysis-depth:P2}") AnalysisDepth maxAnalysisDepth
	) {
		this.evaluationService = evaluationService;
		this.p0EvidenceCollector = p0EvidenceCollector;
		this.p1EvidenceCollector = p1EvidenceCollector;
		this.p2EvidenceCollector = p2EvidenceCollector;
		this.aiAnalysisRequestAssembler = aiAnalysisRequestAssembler;
		this.portfolioReportClient = portfolioReportClient;
		this.aiAnalysisResponseValidator = aiAnalysisResponseValidator;
		this.failureClassifier = failureClassifier;
		this.maxAnalysisDepth = maxAnalysisDepth == null ? AnalysisDepth.P2 : maxAnalysisDepth;
	}

	@Async("evaluationExecutor")
	public void launch(UUID analysisId, String githubAccessToken) {
		if (!runningAnalysisIds.add(analysisId)) {
			log.info("evaluation already running analysisId={}", analysisId);
			return;
		}
		try {
			run(analysisId, githubAccessToken);
		} finally {
			runningAnalysisIds.remove(analysisId);
		}
	}

	private void run(UUID analysisId, String githubAccessToken) {
		log.info("evaluation started analysisId={}", analysisId);
		try {
			Optional<EvaluationInputSnapshot> claimed =
					evaluationService.tryStartCollection(analysisId);
			if (claimed.isEmpty()) {
				log.info("evaluation already claimed analysisId={}", analysisId);
				return;
			}
			EvaluationInputSnapshot inputSnapshot = claimed.get();
			log.info("evaluation collecting evidence analysisId={} maxDepth={}", analysisId, maxAnalysisDepth);
			P0EvidenceSnapshot evidenceSnapshot = collect(githubAccessToken, inputSnapshot);
			AiAnalysisRequest aiRequest = aiAnalysisRequestAssembler.assemble(
					analysisId,
					inputSnapshot,
					evidenceSnapshot
			);
			evaluationService.completeCollection(analysisId, evidenceSnapshot, aiRequest);
			log.info("evaluation analyzing analysisId={}", analysisId);
			AiAnalysisRequest analysisRequest = evaluationService.startAnalysis(analysisId);
			AiAnalysisResponse report = portfolioReportClient.requestReport(analysisRequest);
			aiAnalysisResponseValidator.validate(analysisRequest, report);
			evaluationService.succeed(analysisId, report);
			log.info("evaluation succeeded analysisId={}", analysisId);
		} catch (Exception exception) {
			EvaluationFailureCode code = failureClassifier.classify(exception);
			String reason = safeFailureReason(exception);
			log.warn("evaluation failed analysisId={} code={} reason={}", analysisId, code, reason, exception);
			evaluationService.fail(analysisId, code, reason);
		}
	}

	private P0EvidenceSnapshot collect(
			String githubAccessToken,
			EvaluationInputSnapshot inputSnapshot
	) {
		P0EvidenceSnapshot evidenceSnapshot =
				p0EvidenceCollector.collect(githubAccessToken, inputSnapshot);
		if (!collects(AnalysisDepth.P1)) {
			return evidenceSnapshot;
		}
		evidenceSnapshot = p1EvidenceCollector.collect(
				githubAccessToken,
				inputSnapshot,
				evidenceSnapshot
		);
		if (!collects(AnalysisDepth.P2)) {
			return evidenceSnapshot;
		}
		return p2EvidenceCollector.collect(githubAccessToken, evidenceSnapshot);
	}

	private boolean collects(AnalysisDepth depth) {
		return rank(depth) <= rank(maxAnalysisDepth);
	}

	private int rank(AnalysisDepth depth) {
		return switch (depth) {
			case P0 -> 0;
			case P1 -> 1;
			case P2 -> 2;
		};
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

package com.gitddo.analysis.application;

import com.gitddo.analysis.AnalysisContractFixtures;
import com.gitddo.analysis.client.AiAnalysisClientException;
import com.gitddo.analysis.client.PortfolioReportClient;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.TargetLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationJobLauncherTests {

	@Mock
	private EvaluationService evaluationService;
	@Mock
	private P0EvidenceCollector p0EvidenceCollector;
	@Mock
	private P1EvidenceCollector p1EvidenceCollector;
	@Mock
	private AiAnalysisRequestAssembler aiAnalysisRequestAssembler;
	@Mock
	private PortfolioReportClient portfolioReportClient;
	@Mock
	private AiAnalysisResponseValidator aiAnalysisResponseValidator;

	@InjectMocks
	private EvaluationJobLauncher launcher;

	@Test
	void analyzesAfterEvidenceIsReadyAndStoresValidatedReport() {
		UUID analysisId = UUID.fromString(AnalysisContractFixtures.ANALYSIS_ID);
		EvaluationInputSnapshot input = inputSnapshot();
		P0EvidenceSnapshot evidence = evidenceSnapshot();
		AiAnalysisRequest request = AnalysisContractFixtures.p0Request();
		AiAnalysisResponse report = AnalysisContractFixtures.validP0Report();

		when(evaluationService.startCollection(analysisId)).thenReturn(input);
		when(p0EvidenceCollector.collect("token", input)).thenReturn(evidence);
		when(p1EvidenceCollector.collect("token", input, evidence)).thenReturn(evidence);
		when(aiAnalysisRequestAssembler.assemble(analysisId, input, evidence)).thenReturn(request);
		when(evaluationService.startAnalysis(analysisId)).thenReturn(request);
		when(portfolioReportClient.requestReport(request)).thenReturn(report);

		launcher.launch(analysisId, "token");

		verify(evaluationService).completeCollection(analysisId, evidence, request);
		verify(evaluationService).startAnalysis(analysisId);
		verify(aiAnalysisResponseValidator).validate(request, report);
		verify(evaluationService).succeed(analysisId, report);
		verify(evaluationService, never()).fail(any(), any());
	}

	@Test
	void failsWhenAiServerCallFails() {
		UUID analysisId = UUID.fromString(AnalysisContractFixtures.ANALYSIS_ID);
		EvaluationInputSnapshot input = inputSnapshot();
		P0EvidenceSnapshot evidence = evidenceSnapshot();
		AiAnalysisRequest request = AnalysisContractFixtures.p0Request();

		when(evaluationService.startCollection(analysisId)).thenReturn(input);
		when(p0EvidenceCollector.collect("token", input)).thenReturn(evidence);
		when(p1EvidenceCollector.collect("token", input, evidence)).thenReturn(evidence);
		when(aiAnalysisRequestAssembler.assemble(analysisId, input, evidence)).thenReturn(request);
		when(evaluationService.startAnalysis(analysisId)).thenReturn(request);
		when(portfolioReportClient.requestReport(request))
				.thenThrow(new AiAnalysisClientException("AI 서버가 503를 반환했습니다."));

		launcher.launch(analysisId, "token");

		verify(evaluationService, never()).succeed(any(), any());
		verify(evaluationService).fail(eq(analysisId), contains("AiAnalysisClientException"));
	}

	@Test
	void failsWhenReportValidationFails() {
		UUID analysisId = UUID.fromString(AnalysisContractFixtures.ANALYSIS_ID);
		EvaluationInputSnapshot input = inputSnapshot();
		P0EvidenceSnapshot evidence = evidenceSnapshot();
		AiAnalysisRequest request = AnalysisContractFixtures.p0Request();
		AiAnalysisResponse report = AnalysisContractFixtures.validP0Report();

		when(evaluationService.startCollection(analysisId)).thenReturn(input);
		when(p0EvidenceCollector.collect("token", input)).thenReturn(evidence);
		when(p1EvidenceCollector.collect("token", input, evidence)).thenReturn(evidence);
		when(aiAnalysisRequestAssembler.assemble(analysisId, input, evidence)).thenReturn(request);
		when(evaluationService.startAnalysis(analysisId)).thenReturn(request);
		when(portfolioReportClient.requestReport(request)).thenReturn(report);
		org.mockito.Mockito.doThrow(new InvalidAiAnalysisResponseException("analysisId가 요청과 일치하지 않습니다."))
				.when(aiAnalysisResponseValidator).validate(request, report);

		launcher.launch(analysisId, "token");

		verify(evaluationService, never()).succeed(any(), any());
		verify(evaluationService).fail(eq(analysisId), contains("InvalidAiAnalysisResponseException"));
	}

	private EvaluationInputSnapshot inputSnapshot() {
		return new EvaluationInputSnapshot(
				1,
				1L,
				0L,
				"Backend Portfolio",
				EvaluationPurpose.PORTFOLIO_REVIEW,
				TargetLevel.ENTRY,
				Set.of(EvaluationArea.BACKEND),
				"git-ddo-user",
				List.of()
		);
	}

	private P0EvidenceSnapshot evidenceSnapshot() {
		return new P0EvidenceSnapshot(
				1,
				"p0-collector-1.0",
				Instant.parse("2026-08-18T00:00:00Z"),
				List.of(),
				List.of(),
				List.of()
		);
	}
}

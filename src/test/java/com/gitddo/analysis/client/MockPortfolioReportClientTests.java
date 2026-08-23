package com.gitddo.analysis.client;

import com.gitddo.analysis.AnalysisContractFixtures;
import com.gitddo.analysis.application.AiAnalysisResponseValidator;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.FindingCategory;
import com.gitddo.analysis.contract.LimitationCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MockPortfolioReportClientTests {

	@Test
	void buildsP0OnlyReportTiedToRequest() {
		AiAnalysisRequest request = AnalysisContractFixtures.p0Request();
		AiAnalysisResponse report = new MockPortfolioReportClient().requestReport(request);

		assertThat(report.analysisId()).isEqualTo(request.analysisId());
		assertThat(report.requestedAnalysisDepth()).isEqualTo(AnalysisDepth.P0);
		assertThat(report.usedEvidenceLevels()).containsExactly(AnalysisDepth.P0);
		assertThat(report.repositories()).singleElement().satisfies(repository -> {
			assertThat(repository.snapshotSha()).isEqualTo("commit-sha");
			assertThat(repository.findings())
					.isNotEmpty()
					.allMatch(finding -> finding.category() != FindingCategory.CODE_QUALITY)
					.allMatch(finding -> finding.category() != FindingCategory.ACTIVITY)
					.allMatch(finding -> finding.evidenceRefs().stream()
							.allMatch(id -> id.equals("ev_001") || id.equals("ev_002")));
		});
		assertThat(report.limitations())
				.extracting(AiAnalysisResponse.Limitation::code)
				.contains(LimitationCode.P0_ONLY);
		assertThatCode(() -> new AiAnalysisResponseValidator().validate(request, report))
				.doesNotThrowAnyException();
	}

	@Test
	void buildsP1ReportWithActivityAndContributionFindings() {
		AiAnalysisRequest request = AnalysisContractFixtures.p1Request();
		AiAnalysisResponse report = new MockPortfolioReportClient().requestReport(request);

		assertThat(report.requestedAnalysisDepth()).isEqualTo(AnalysisDepth.P1);
		assertThat(report.usedEvidenceLevels()).containsExactly(AnalysisDepth.P0, AnalysisDepth.P1);
		assertThat(report.limitations())
				.extracting(AiAnalysisResponse.Limitation::code)
				.contains(LimitationCode.MISSING_CODE_EVIDENCE)
				.doesNotContain(LimitationCode.P0_ONLY);
		assertThat(report.coaching().jobAppeal().evidenceRefs()).isNotEmpty();
		assertThat(report.coaching().interviewQuestions()).isNotEmpty()
				.allMatch(question -> !question.evidenceRefs().isEmpty() || !question.claimRefs().isEmpty());
		assertThat(report.coaching().nextActions())
				.allMatch(item -> !item.evidenceRefs().isEmpty());
		assertThat(report.repositories()).singleElement().satisfies(repository -> {
			assertThat(repository.findings())
					.extracting(AiAnalysisResponse.Finding::category)
					.contains(FindingCategory.DOCUMENTATION, FindingCategory.ACTIVITY, FindingCategory.CONTRIBUTION)
					.doesNotContain(FindingCategory.CODE_QUALITY);
			assertThat(repository.findings())
					.filteredOn(finding -> finding.category() == FindingCategory.ACTIVITY)
					.isNotEmpty()
					.allMatch(finding -> finding.evidenceRefs().contains("ev_003")
							|| finding.evidenceRefs().contains("ev_004"));
		});
		assertThatCode(() -> new AiAnalysisResponseValidator().validate(request, report))
				.doesNotThrowAnyException();
	}

	@Test
	void buildsP2ReportWithCodeQualityFindings() {
		AiAnalysisRequest request = AnalysisContractFixtures.p2Request();
		AiAnalysisResponse report = new MockPortfolioReportClient().requestReport(request);

		assertThat(report.requestedAnalysisDepth()).isEqualTo(AnalysisDepth.P2);
		assertThat(report.usedEvidenceLevels())
				.containsExactly(AnalysisDepth.P0, AnalysisDepth.P1, AnalysisDepth.P2);
		assertThat(report.limitations())
				.extracting(AiAnalysisResponse.Limitation::code)
				.doesNotContain(LimitationCode.MISSING_CODE_EVIDENCE, LimitationCode.P0_ONLY);
		assertThat(report.repositories()).singleElement().satisfies(repository ->
				assertThat(repository.findings())
						.filteredOn(finding -> finding.category() == FindingCategory.CODE_QUALITY)
						.isNotEmpty()
						.allMatch(finding -> finding.evidenceRefs().contains("ev_005")));
		assertThat(report.coaching().interviewQuestions().getFirst().evidenceRefs()).contains("ev_005");
		assertThatCode(() -> new AiAnalysisResponseValidator().validate(request, report))
				.doesNotThrowAnyException();
	}
}

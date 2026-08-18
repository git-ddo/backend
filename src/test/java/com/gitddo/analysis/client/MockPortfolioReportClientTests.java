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
}

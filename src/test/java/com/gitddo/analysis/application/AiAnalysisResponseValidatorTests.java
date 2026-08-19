package com.gitddo.analysis.application;

import com.gitddo.analysis.AnalysisContractFixtures;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.FindingCategory;
import com.gitddo.analysis.contract.FindingSeverity;
import com.gitddo.analysis.contract.SnapshotHashAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAnalysisResponseValidatorTests {

	private final AiAnalysisResponseValidator validator = new AiAnalysisResponseValidator();

	@Test
	void acceptsReportBoundToRequestIdsAndSha() {
		assertThatCode(() -> validator.validate(
				AnalysisContractFixtures.p0Request(),
				AnalysisContractFixtures.validP0Report()
		)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMismatchedAnalysisId() {
		AiAnalysisResponse report = withAnalysisId("22222222-2222-4222-8222-222222222222");
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("analysisId");
	}

	@Test
	void rejectsUnknownEvidenceId() {
		AiAnalysisResponse report = withEvidenceRefs(List.of("ev_999"));
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("ev_999");
	}

	@Test
	void rejectsMismatchedSnapshotSha() {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse report = new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				base.requestedAnalysisDepth(),
				base.usedEvidenceLevels(),
				base.summary(),
				List.of(new AiAnalysisResponse.RepositoryReport(
						"123",
						"git-ddo/backend",
						SnapshotHashAlgorithm.SHA1,
						"other-sha",
						base.repositories().getFirst().findings()
				)),
				base.coaching(),
				base.limitations()
		);
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("snapshotSha");
	}

	@Test
	void rejectsCodeQualityWhenOnlyP0WasUsed() {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse report = new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				base.requestedAnalysisDepth(),
				base.usedEvidenceLevels(),
				base.summary(),
				List.of(new AiAnalysisResponse.RepositoryReport(
						"123",
						"git-ddo/backend",
						SnapshotHashAlgorithm.SHA1,
						AnalysisContractFixtures.SNAPSHOT_SHA,
						List.of(new AiAnalysisResponse.Finding(
								"find_001",
								FindingCategory.CODE_QUALITY,
								FindingSeverity.RISK,
								"코드 품질이 낮습니다.",
								"P0만 보고 단정했습니다.",
								List.of("ev_001"),
								List.of()
						))
				)),
				base.coaching(),
				base.limitations()
		);
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("CODE_QUALITY");
	}

	@Test
	void rejectsUsedLevelDeeperThanRequested() {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse report = new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				AnalysisDepth.P0,
				List.of(AnalysisDepth.P0, AnalysisDepth.P1),
				base.summary(),
				base.repositories(),
				base.coaching(),
				base.limitations()
		);
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class);
	}

	@Test
	void rejectsDuplicateFindingIdsAcrossTheAnalysis() {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse.Finding finding = base.repositories().getFirst().findings().getFirst();
		AiAnalysisResponse report = new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				base.requestedAnalysisDepth(),
				base.usedEvidenceLevels(),
				base.summary(),
				List.of(new AiAnalysisResponse.RepositoryReport(
						"123",
						"git-ddo/backend",
						SnapshotHashAlgorithm.SHA1,
						AnalysisContractFixtures.SNAPSHOT_SHA,
						List.of(finding, finding)
				)),
				base.coaching(),
				base.limitations()
		);
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("findingId");
	}

	@Test
	void rejectsNextActionsWithoutEvidence() {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse.Coaching coaching = base.coaching();
		AiAnalysisResponse report = new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				base.requestedAnalysisDepth(),
				base.usedEvidenceLevels(),
				base.summary(),
				base.repositories(),
				new AiAnalysisResponse.Coaching(
						coaching.strengths(),
						coaching.gaps(),
						List.of(new AiAnalysisResponse.CoachingItem("P2를 수행하세요.", List.of())),
						coaching.jobAppeal(),
						coaching.portfolioStatements(),
						coaching.interviewQuestions()
				),
				base.limitations()
		);
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p0Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("nextActions");
	}

	@Test
	void rejectsActivityFindingThatDoesNotCiteP1Evidence() {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse report = new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				AnalysisDepth.P1,
				List.of(AnalysisDepth.P0, AnalysisDepth.P1),
				base.summary(),
				List.of(new AiAnalysisResponse.RepositoryReport(
						"123",
						"git-ddo/backend",
						SnapshotHashAlgorithm.SHA1,
						AnalysisContractFixtures.SNAPSHOT_SHA,
						List.of(new AiAnalysisResponse.Finding(
								"find_001",
								FindingCategory.ACTIVITY,
								FindingSeverity.POSITIVE,
								"기여가 큽니다.",
								"README만 보고 활동을 단정했습니다.",
								List.of("ev_001"),
								List.of()
						))
				)),
				base.coaching(),
				List.of()
		);
		assertThatThrownBy(() -> validator.validate(AnalysisContractFixtures.p1Request(), report))
				.isInstanceOf(InvalidAiAnalysisResponseException.class)
				.hasMessageContaining("P1");
	}

	private AiAnalysisResponse withAnalysisId(String analysisId) {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		return new AiAnalysisResponse(
				base.schemaVersion(),
				analysisId,
				base.evaluatorVersion(),
				base.requestedAnalysisDepth(),
				base.usedEvidenceLevels(),
				base.summary(),
				base.repositories(),
				base.coaching(),
				base.limitations()
		);
	}

	private AiAnalysisResponse withEvidenceRefs(List<String> evidenceRefs) {
		AiAnalysisResponse base = AnalysisContractFixtures.validP0Report();
		AiAnalysisResponse.Finding finding = base.repositories().getFirst().findings().getFirst();
		return new AiAnalysisResponse(
				base.schemaVersion(),
				base.analysisId(),
				base.evaluatorVersion(),
				base.requestedAnalysisDepth(),
				base.usedEvidenceLevels(),
				base.summary(),
				List.of(new AiAnalysisResponse.RepositoryReport(
						"123",
						"git-ddo/backend",
						SnapshotHashAlgorithm.SHA1,
						AnalysisContractFixtures.SNAPSHOT_SHA,
						List.of(new AiAnalysisResponse.Finding(
								finding.findingId(),
								finding.category(),
								finding.severity(),
								finding.title(),
								finding.detail(),
								evidenceRefs,
								finding.claimRefs()
						))
				)),
				base.coaching(),
				base.limitations()
		);
	}
}

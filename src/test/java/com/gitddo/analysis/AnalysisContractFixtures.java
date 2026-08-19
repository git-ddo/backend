package com.gitddo.analysis;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.AnalysisPurpose;
import com.gitddo.analysis.contract.EvidenceValueType;
import com.gitddo.analysis.contract.FindingCategory;
import com.gitddo.analysis.contract.FindingSeverity;
import com.gitddo.analysis.contract.LimitationCode;
import com.gitddo.analysis.contract.SnapshotHashAlgorithm;
import com.gitddo.analysis.contract.TargetCareerLevel;
import com.gitddo.analysis.contract.TargetJob;

import java.util.List;

public final class AnalysisContractFixtures {

	public static final String ANALYSIS_ID = "11111111-1111-4111-8111-111111111111";
	public static final String SNAPSHOT_SHA = "commit-sha";

	private AnalysisContractFixtures() {
	}

	public static AiAnalysisRequest p0Request() {
		return new AiAnalysisRequest(
				AiAnalysisRequest.SCHEMA_VERSION,
				ANALYSIS_ID,
				TargetJob.BACKEND,
				TargetCareerLevel.ENTRY,
				AnalysisPurpose.PORTFOLIO_ANALYSIS,
				AnalysisDepth.P0,
				"p0-collector-1.0",
				List.of(new AiAnalysisRequest.Repository(
						"123",
						"git-ddo/backend",
						"main",
						SnapshotHashAlgorithm.SHA1,
						SNAPSHOT_SHA,
						List.of(AnalysisDepth.P0),
						List.of(),
						List.of(new AiAnalysisRequest.UserClaim(
								"claim_001",
								"API를 구현했습니다.",
								"LEAD",
								null,
								null,
								List.of()
						)),
						List.of(
								evidence("ev_001", "README", "README.md", "# Backend"),
								evidence("ev_002", "BUILD_MANIFEST", "build.gradle", "plugins { id 'java' }")
						)
				))
		);
	}

	public static AiAnalysisRequest p1Request() {
		return new AiAnalysisRequest(
				AiAnalysisRequest.SCHEMA_VERSION,
				ANALYSIS_ID,
				TargetJob.BACKEND,
				TargetCareerLevel.ENTRY,
				AnalysisPurpose.PORTFOLIO_ANALYSIS,
				AnalysisDepth.P1,
				"p0-p1-collector-1.0",
				List.of(new AiAnalysisRequest.Repository(
						"123",
						"git-ddo/backend",
						"main",
						SnapshotHashAlgorithm.SHA1,
						SNAPSHOT_SHA,
						List.of(AnalysisDepth.P0, AnalysisDepth.P1),
						List.of(),
						List.of(new AiAnalysisRequest.UserClaim(
								"claim_001",
								"API를 구현했습니다.",
								"LEAD",
								null,
								null,
								List.of("ev_003")
						)),
						List.of(
								evidence("ev_001", "README", "README.md", "# Backend"),
								evidence("ev_002", "BUILD_MANIFEST", "build.gradle", "plugins { id 'java' }"),
								activity("ev_003", "COMMIT_SUMMARY", "abc123", null, "sha=abc123"),
								activity("ev_004", "PULL_REQUEST", "def456", 12, "number=12")
						)
				))
		);
	}

	static AiAnalysisRequest.Evidence evidence(
			String evidenceId,
			String factKey,
			String path,
			String value
	) {
		return new AiAnalysisRequest.Evidence(
				evidenceId,
				"GITHUB_STATIC",
				AnalysisDepth.P0,
				"123",
				"git-ddo/backend",
				SnapshotHashAlgorithm.SHA1,
				SNAPSHOT_SHA,
				factKey,
				EvidenceValueType.STRING,
				value,
				path,
				null,
				null,
				SNAPSHOT_SHA,
				null,
				List.of(),
				null
		);
	}

	static AiAnalysisRequest.Evidence activity(
			String evidenceId,
			String factKey,
			String commitSha,
			Integer pullRequestNumber,
			String value
	) {
		return new AiAnalysisRequest.Evidence(
				evidenceId,
				"GITHUB_ACTIVITY",
				AnalysisDepth.P1,
				"123",
				"git-ddo/backend",
				SnapshotHashAlgorithm.SHA1,
				SNAPSHOT_SHA,
				factKey,
				EvidenceValueType.STRING,
				value,
				null,
				null,
				null,
				commitSha,
				pullRequestNumber,
				List.of(),
				null
		);
	}

	public static AiAnalysisResponse validP0Report() {
		return new AiAnalysisResponse(
				AiAnalysisResponse.SCHEMA_VERSION,
				ANALYSIS_ID,
				"mock-1.0",
				AnalysisDepth.P0,
				List.of(AnalysisDepth.P0),
				"BACKEND ENTRY 포트폴리오를 P0 근거만으로 해석했습니다.",
				List.of(new AiAnalysisResponse.RepositoryReport(
						"123",
						"git-ddo/backend",
						SnapshotHashAlgorithm.SHA1,
						SNAPSHOT_SHA,
						List.of(new AiAnalysisResponse.Finding(
								"find_001",
								FindingCategory.DOCUMENTATION,
								FindingSeverity.POSITIVE,
								"문서 근거가 있습니다.",
								"README가 있습니다.",
								List.of("ev_001"),
								List.of()
						))
				)),
				new AiAnalysisResponse.Coaching(
						List.of(new AiAnalysisResponse.CoachingItem("README를 확인할 수 있습니다.", List.of("ev_001"))),
						List.of(new AiAnalysisResponse.CoachingItem(
								"빌드 설정을 포트폴리오 설명과 연결하세요.",
								List.of("ev_002")
						)),
						List.of(new AiAnalysisResponse.CoachingItem(
								"README에 실행 방법을 구체적으로 적으세요.",
								List.of("ev_001")
						)),
						new AiAnalysisResponse.JobAppeal(
								"문서와 스택 Evidence로 백엔드 학습 경험을 어필할 수 있습니다.",
								List.of("ev_001")
						),
						List.of(new AiAnalysisResponse.PortfolioStatement(
								"API를 구현했습니다.",
								List.of("ev_001"),
								List.of("claim_001")
						)),
						List.of(new AiAnalysisResponse.InterviewQuestion(
								"디렉터리 구조를 어떻게 설명하시겠어요?",
								"P0 구조 근거를 말로 재구성하는지 확인합니다.",
								"README와 빌드 매니페스트를 기준으로 설명하면 됩니다.",
								List.of("ev_001"),
								List.of()
						))
				),
				List.of(new AiAnalysisResponse.Limitation(
						LimitationCode.P0_ONLY,
						"P0만 해석했습니다."
				))
		);
	}
}

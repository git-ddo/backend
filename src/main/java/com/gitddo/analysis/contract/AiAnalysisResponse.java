package com.gitddo.analysis.contract;

import java.util.List;

public record AiAnalysisResponse(
		String schemaVersion,
		String analysisId,
		String evaluatorVersion,
		AnalysisDepth requestedAnalysisDepth,
		List<AnalysisDepth> usedEvidenceLevels,
		String summary,
		List<RepositoryReport> repositories,
		Coaching coaching,
		List<Limitation> limitations
) {

	public static final String SCHEMA_VERSION = "1.0";

	public AiAnalysisResponse {
		usedEvidenceLevels = usedEvidenceLevels == null ? List.of() : List.copyOf(usedEvidenceLevels);
		repositories = repositories == null ? List.of() : List.copyOf(repositories);
		limitations = limitations == null ? List.of() : List.copyOf(limitations);
	}

	public record RepositoryReport(
			String repositoryId,
			String repositoryFullName,
			SnapshotHashAlgorithm snapshotHashAlgorithm,
			String snapshotSha,
			List<Finding> findings
	) {

		public RepositoryReport {
			findings = findings == null ? List.of() : List.copyOf(findings);
		}
	}

	public record Finding(
			String findingId,
			FindingCategory category,
			FindingSeverity severity,
			String title,
			String detail,
			List<String> evidenceRefs,
			List<String> claimRefs
	) {

		public Finding {
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
			claimRefs = claimRefs == null ? List.of() : List.copyOf(claimRefs);
		}
	}

	public record Coaching(
			List<CoachingItem> strengths,
			List<CoachingItem> gaps,
			List<CoachingItem> nextActions,
			List<String> interviewQuestions
	) {

		public Coaching {
			strengths = strengths == null ? List.of() : List.copyOf(strengths);
			gaps = gaps == null ? List.of() : List.copyOf(gaps);
			nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
			interviewQuestions = interviewQuestions == null ? List.of() : List.copyOf(interviewQuestions);
		}
	}

	public record CoachingItem(
			String text,
			List<String> evidenceRefs
	) {

		public CoachingItem {
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
		}
	}

	public record Limitation(
			LimitationCode code,
			String message
	) {
	}
}

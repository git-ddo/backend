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

	public static final String SCHEMA_VERSION = "1.1";

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
			Confidence confidence,
			String title,
			String detail,
			List<String> evidenceRefs,
			List<String> claimRefs,
			List<String> filePaths
	) {

		public Finding {
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
			claimRefs = claimRefs == null ? List.of() : List.copyOf(claimRefs);
			filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
		}
	}

	public record Coaching(
			List<CoachingItem> strengths,
			List<CoachingItem> gaps,
			List<CoachingItem> nextActions,
			JobAppeal jobAppeal,
			List<PortfolioStatement> portfolioStatements,
			List<InterviewQuestion> interviewQuestions
	) {

		public Coaching {
			strengths = strengths == null ? List.of() : List.copyOf(strengths);
			gaps = gaps == null ? List.of() : List.copyOf(gaps);
			nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
			portfolioStatements = portfolioStatements == null ? List.of() : List.copyOf(portfolioStatements);
			interviewQuestions = interviewQuestions == null ? List.of() : List.copyOf(interviewQuestions);
		}
	}

	public record CoachingItem(
			String text,
			Confidence confidence,
			List<String> evidenceRefs
	) {

		public CoachingItem {
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
		}
	}

	public record JobAppeal(
			String text,
			Confidence confidence,
			List<String> evidenceRefs
	) {

		public JobAppeal {
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
		}
	}

	public record PortfolioStatement(
			String text,
			Confidence confidence,
			List<String> evidenceRefs,
			List<String> claimRefs
	) {

		public PortfolioStatement {
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
			claimRefs = claimRefs == null ? List.of() : List.copyOf(claimRefs);
		}
	}

	public record InterviewQuestion(
			String question,
			String intent,
			List<String> answerGuide,
			List<String> followUpQuestions,
			Confidence confidence,
			List<String> evidenceRefs,
			List<String> claimRefs
	) {

		public InterviewQuestion {
			answerGuide = answerGuide == null ? List.of() : List.copyOf(answerGuide);
			followUpQuestions = followUpQuestions == null ? List.of() : List.copyOf(followUpQuestions);
			evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
			claimRefs = claimRefs == null ? List.of() : List.copyOf(claimRefs);
		}
	}

	public record Limitation(
			LimitationCode code,
			String message
	) {
	}
}

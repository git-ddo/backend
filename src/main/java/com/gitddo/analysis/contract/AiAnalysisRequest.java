package com.gitddo.analysis.contract;

import java.time.LocalDate;
import java.util.List;

public record AiAnalysisRequest(
		String schemaVersion,
		String analysisId,
		TargetJob targetJob,
		TargetCareerLevel targetCareerLevel,
		AnalysisPurpose analysisPurpose,
		AnalysisDepth requestedAnalysisDepth,
		String extractorVersion,
		List<Repository> repositories
) {

	public static final String SCHEMA_VERSION = "1.0";

	public AiAnalysisRequest {
		repositories = List.copyOf(repositories);
	}

	public record Repository(
			String repositoryId,
			String repositoryFullName,
			String defaultBranch,
			SnapshotHashAlgorithm snapshotHashAlgorithm,
			String snapshotSha,
			List<AnalysisDepth> completedEvidenceLevels,
			List<CollectionWarning> collectionWarnings,
			List<UserClaim> userClaims,
			List<Evidence> evidence
	) {

		public Repository {
			completedEvidenceLevels = List.copyOf(completedEvidenceLevels);
			collectionWarnings = List.copyOf(collectionWarnings);
			userClaims = List.copyOf(userClaims);
			evidence = List.copyOf(evidence);
		}
	}

	public record CollectionWarning(
			String code,
			String path,
			String message
	) {
	}

	public record UserClaim(
			String claimId,
			String statement,
			String participationLevel,
			LocalDate participationStartedOn,
			LocalDate participationEndedOn,
			List<String> relatedEvidenceRefs
	) {

		public UserClaim {
			relatedEvidenceRefs = List.copyOf(relatedEvidenceRefs);
		}
	}

	public record Evidence(
			String evidenceId,
			String evidenceType,
			AnalysisDepth analysisDepth,
			String repositoryId,
			String repositoryFullName,
			SnapshotHashAlgorithm snapshotHashAlgorithm,
			String snapshotSha,
			String factKey,
			EvidenceValueType valueType,
			String value,
			String path,
			Integer startLine,
			Integer endLine,
			String commitSha,
			Integer pullRequestNumber,
			List<String> sourceEvidenceRefs,
			AnalysisDepth derivedFromLevel
	) {

		public Evidence {
			sourceEvidenceRefs = List.copyOf(sourceEvidenceRefs);
		}
	}
}

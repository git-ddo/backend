package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.AnalysisPurpose;
import com.gitddo.analysis.contract.EvidenceValueType;
import com.gitddo.analysis.contract.SnapshotHashAlgorithm;
import com.gitddo.analysis.contract.TargetCareerLevel;
import com.gitddo.analysis.contract.TargetJob;
import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvidenceType;
import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.TargetLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AiAnalysisRequestAssembler {

	public AiAnalysisRequest assemble(
			UUID analysisId,
			EvaluationInputSnapshot inputSnapshot,
			P0EvidenceSnapshot evidenceSnapshot
	) {
		Map<String, EvaluationInputSnapshot.RepositorySnapshot> inputByRepositoryId =
				inputSnapshot.repositories().stream()
						.collect(Collectors.toMap(
								repository -> String.valueOf(repository.githubRepositoryId()),
								repository -> repository
						));
		List<AiAnalysisRequest.Repository> repositories = new ArrayList<>();
		int claimSequence = 1;
		for (P0EvidenceSnapshot.RepositorySnapshot repository : evidenceSnapshot.repositories()) {
			ClaimAssignment claims = userClaims(
					claimSequence,
					inputByRepositoryId.get(repository.repositoryId())
			);
			claimSequence = claims.nextSequence();
			repositories.add(toRepository(
					repository,
					evidenceSnapshot,
					claims.claims()
			));
		}
		return new AiAnalysisRequest(
				AiAnalysisRequest.SCHEMA_VERSION,
				analysisId.toString(),
				targetJob(inputSnapshot),
				targetCareerLevel(inputSnapshot.targetLevel()),
				AnalysisPurpose.PORTFOLIO_ANALYSIS,
				requestedDepth(repositories),
				evidenceSnapshot.extractorVersion(),
				repositories
		);
	}

	private AiAnalysisRequest.Repository toRepository(
			P0EvidenceSnapshot.RepositorySnapshot repository,
			P0EvidenceSnapshot evidenceSnapshot,
			List<AiAnalysisRequest.UserClaim> userClaims
	) {
		List<P0EvidenceSnapshot.Evidence> repositoryEvidence = evidenceSnapshot.evidence().stream()
				.filter(evidence -> repository.repositoryId().equals(evidence.repositoryId()))
				.toList();
		SnapshotHashAlgorithm hashAlgorithm = hashAlgorithm(repository.snapshotSha());
		return new AiAnalysisRequest.Repository(
				repository.repositoryId(),
				repository.fullName(),
				defaultBranch(repositoryEvidence),
				hashAlgorithm,
				repository.snapshotSha(),
				completedLevels(repositoryEvidence),
				collectionWarnings(repository.repositoryId(), evidenceSnapshot),
				userClaims,
				repositoryEvidence.stream()
						.map(evidence -> toEvidence(repository, hashAlgorithm, evidence))
						.toList()
		);
	}

	private AiAnalysisRequest.Evidence toEvidence(
			P0EvidenceSnapshot.RepositorySnapshot repository,
			SnapshotHashAlgorithm hashAlgorithm,
			P0EvidenceSnapshot.Evidence evidence
	) {
		boolean derived = evidence.evidenceType() == EvidenceType.BACKEND_DERIVED;
		AnalysisDepth depth = evidence.analysisDepth() == null
				? AnalysisDepth.P0
				: evidence.analysisDepth();
		return new AiAnalysisRequest.Evidence(
				evidence.evidenceId(),
				evidence.evidenceType().name(),
				depth,
				repository.repositoryId(),
				repository.fullName(),
				hashAlgorithm,
				repository.snapshotSha(),
				evidence.kind().name(),
				EvidenceValueType.STRING,
				evidence.content() == null ? "" : evidence.content(),
				evidence.path(),
				evidence.startLine(),
				evidence.endLine(),
				evidence.commitSha() == null ? repository.snapshotSha() : evidence.commitSha(),
				evidence.pullRequestNumber(),
				evidence.sourceEvidenceRefs(),
				derived ? depth : null
		);
	}

	private List<AiAnalysisRequest.CollectionWarning> collectionWarnings(
			String repositoryId,
			P0EvidenceSnapshot evidenceSnapshot
	) {
		List<AiAnalysisRequest.CollectionWarning> warnings = new ArrayList<>();
		evidenceSnapshot.warnings().stream()
				.filter(warning -> repositoryId.equals(warning.repositoryId()))
				.forEach(warning -> warnings.add(new AiAnalysisRequest.CollectionWarning(
						warning.code(),
						warning.path(),
						warning.message()
				)));
		evidenceSnapshot.evidence().stream()
				.filter(evidence -> repositoryId.equals(evidence.repositoryId()))
				.filter(P0EvidenceSnapshot.Evidence::truncated)
				.forEach(evidence -> warnings.add(new AiAnalysisRequest.CollectionWarning(
						"TRUNCATED_INPUT",
						evidence.path(),
						evidence.kind().name() + " 내용이 잘려서 전달됩니다."
				)));
		return warnings;
	}

	private ClaimAssignment userClaims(
			int startSequence,
			EvaluationInputSnapshot.RepositorySnapshot input
	) {
		if (input == null) {
			return new ClaimAssignment(startSequence, List.of());
		}
		List<AiAnalysisRequest.UserClaim> claims = new ArrayList<>();
		int sequence = startSequence;
		List<EvaluationInputSnapshot.RoleSnapshot> roles =
				input.roles() == null ? List.of() : input.roles();
		String participationLevel = roles.stream()
				.filter(EvaluationInputSnapshot.RoleSnapshot::primary)
				.map(role -> role.participationLevel().name())
				.findFirst()
				.orElse(null);
		if (input.contributionDescription() != null && !input.contributionDescription().isBlank()) {
			claims.add(new AiAnalysisRequest.UserClaim(
					"claim_%03d".formatted(sequence++),
					input.contributionDescription(),
					participationLevel,
					null,
					null,
					List.of()
			));
		}
		if (input.roleSummary() != null && !input.roleSummary().isBlank()) {
			claims.add(new AiAnalysisRequest.UserClaim(
					"claim_%03d".formatted(sequence++),
					input.roleSummary(),
					participationLevel,
					null,
					null,
					List.of()
			));
		}
		return new ClaimAssignment(sequence, claims);
	}

	private List<AnalysisDepth> completedLevels(List<P0EvidenceSnapshot.Evidence> evidence) {
		return evidence.stream()
				.map(item -> item.analysisDepth() == null ? AnalysisDepth.P0 : item.analysisDepth())
				.distinct()
				.sorted(Comparator.comparingInt(this::rank))
				.toList();
	}

	private AnalysisDepth requestedDepth(List<AiAnalysisRequest.Repository> repositories) {
		return repositories.stream()
				.flatMap(repository -> repository.completedEvidenceLevels().stream())
				.max(Comparator.comparingInt(this::rank))
				.orElse(AnalysisDepth.P0);
	}

	private int rank(AnalysisDepth depth) {
		return switch (depth) {
			case P0 -> 0;
			case P1 -> 1;
			case P2 -> 2;
		};
	}

	private String defaultBranch(List<P0EvidenceSnapshot.Evidence> evidence) {
		return evidence.stream()
				.filter(item -> item.kind() == EvidenceKind.REPOSITORY_METADATA)
				.map(P0EvidenceSnapshot.Evidence::content)
				.flatMap(content -> content.lines())
				.filter(line -> line.startsWith("defaultBranch="))
				.map(line -> line.substring("defaultBranch=".length()))
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse(null);
	}

	private SnapshotHashAlgorithm hashAlgorithm(String snapshotSha) {
		if (snapshotSha != null && snapshotSha.length() == 64) {
			return SnapshotHashAlgorithm.SHA256;
		}
		return SnapshotHashAlgorithm.SHA1;
	}

	private TargetJob targetJob(EvaluationInputSnapshot inputSnapshot) {
		if (inputSnapshot.evaluationAreas().contains(EvaluationArea.BACKEND)) {
			return TargetJob.BACKEND;
		}
		throw new UnsupportedAnalysisCombinationException();
	}

	private TargetCareerLevel targetCareerLevel(TargetLevel targetLevel) {
		if (targetLevel == null) {
			return TargetCareerLevel.ENTRY;
		}
		return TargetCareerLevel.valueOf(targetLevel.name());
	}

	private record ClaimAssignment(
			int nextSequence,
			List<AiAnalysisRequest.UserClaim> claims
	) {
	}
}

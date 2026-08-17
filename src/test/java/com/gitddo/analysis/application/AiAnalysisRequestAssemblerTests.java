package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.AnalysisPurpose;
import com.gitddo.analysis.contract.EvidenceValueType;
import com.gitddo.analysis.contract.TargetCareerLevel;
import com.gitddo.analysis.contract.TargetJob;
import com.gitddo.analysis.domain.P0EvidenceKind;
import com.gitddo.github.client.GithubAnalysisClient;
import com.gitddo.github.client.GithubBlobPayload;
import com.gitddo.github.client.GithubCommitSnapshotPayload;
import com.gitddo.github.client.GithubRepositoryPayload;
import com.gitddo.github.client.GithubTreeEntryPayload;
import com.gitddo.github.client.GithubTreePayload;
import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.TargetLevel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAnalysisRequestAssemblerTests {

	@Test
	void assemblesContractRequestFromP0Evidence() {
		P0EvidenceCollector collector = collector();
		var input = inputSnapshot();
		var evidence = collector.collect("token", input);
		UUID analysisId = UUID.fromString("11111111-1111-4111-8111-111111111111");

		AiAnalysisRequest request = new AiAnalysisRequestAssembler()
				.assemble(analysisId, input, evidence);

		assertThat(request.schemaVersion()).isEqualTo("1.0");
		assertThat(request.analysisId()).isEqualTo(analysisId.toString());
		assertThat(request.targetJob()).isEqualTo(TargetJob.BACKEND);
		assertThat(request.targetCareerLevel()).isEqualTo(TargetCareerLevel.ENTRY);
		assertThat(request.analysisPurpose()).isEqualTo(AnalysisPurpose.PORTFOLIO_ANALYSIS);
		assertThat(request.requestedAnalysisDepth()).isEqualTo(AnalysisDepth.P0);
		assertThat(request.repositories()).singleElement().satisfies(repository -> {
			assertThat(repository.repositoryId()).isEqualTo("123");
			assertThat(repository.repositoryFullName()).isEqualTo("git-ddo/backend");
			assertThat(repository.defaultBranch()).isEqualTo("main");
			assertThat(repository.snapshotSha()).isEqualTo("commit-sha");
			assertThat(repository.completedEvidenceLevels()).containsExactly(AnalysisDepth.P0);
			assertThat(repository.userClaims())
					.extracting(AiAnalysisRequest.UserClaim::claimId)
					.containsExactly("claim_001", "claim_002");
			assertThat(repository.userClaims().getFirst().statement())
					.isEqualTo("API를 구현했습니다.");
			assertThat(repository.evidence())
					.extracting(AiAnalysisRequest.Evidence::factKey)
					.contains(
							P0EvidenceKind.README.name(),
							P0EvidenceKind.BUILD_MANIFEST.name(),
							P0EvidenceKind.PROJECT_STRUCTURE.name()
					);
			assertThat(repository.evidence())
					.allMatch(item -> item.valueType() == EvidenceValueType.STRING);
			assertThat(repository.evidence())
					.filteredOn(item -> "BACKEND_DERIVED".equals(item.evidenceType()))
					.singleElement()
					.satisfies(item -> {
						assertThat(item.derivedFromLevel()).isEqualTo(AnalysisDepth.P0);
						assertThat(item.sourceEvidenceRefs()).isNotEmpty();
					});
		});
	}

	@Test
	void rejectsRequestWithoutBackendArea() {
		var input = new com.gitddo.analysis.domain.EvaluationInputSnapshot(
				1,
				1L,
				0L,
				"Frontend Portfolio",
				EvaluationPurpose.PORTFOLIO_REVIEW,
				TargetLevel.ENTRY,
				Set.of(EvaluationArea.FRONTEND),
				List.of()
		);
		assertThatThrownBy(() ->
				new AiAnalysisRequestAssembler().assemble(
						UUID.randomUUID(),
						input,
						new com.gitddo.analysis.domain.P0EvidenceSnapshot(
								1,
								"p0-collector-1.0",
								Instant.now(),
								List.of(),
								List.of(),
								List.of()
						)
				))
				.isInstanceOf(UnsupportedAnalysisCombinationException.class);
	}

	private P0EvidenceCollector collector() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		when(client.fetchRepository("token", "git-ddo", "backend"))
				.thenReturn(new GithubRepositoryPayload(
						123L,
						"backend",
						"git-ddo/backend",
						"Backend service",
						"https://github.com/git-ddo/backend",
						"Java",
						false,
						false,
						"main",
						1,
						0,
						Instant.parse("2026-08-17T00:00:00Z"),
						Instant.parse("2026-08-17T00:00:00Z")
				));
		when(client.fetchCommitSnapshot("token", "git-ddo", "backend", "main"))
				.thenReturn(new GithubCommitSnapshotPayload(
						"commit-sha",
						new GithubCommitSnapshotPayload.CommitPayload(
								new GithubCommitSnapshotPayload.TreePayload("tree-sha")
						)
				));
		when(client.fetchLanguages("token", "git-ddo", "backend"))
				.thenReturn(Map.of("Java", 10_000L));
		when(client.fetchTree("token", "git-ddo", "backend", "tree-sha"))
				.thenReturn(new GithubTreePayload(
						"tree-sha",
						false,
						List.of(
								entry("README.md", "readme-sha", 12L),
								entry("build.gradle", "build-sha", 20L)
						)
				));
		when(client.fetchBlob("token", "git-ddo", "backend", "readme-sha"))
				.thenReturn(blob("readme-sha", "# Backend"));
		when(client.fetchBlob("token", "git-ddo", "backend", "build-sha"))
				.thenReturn(blob("build-sha", "plugins { id 'java' }"));
		return new P0EvidenceCollector(client, new P0FileSelectionPolicy());
	}

	private com.gitddo.analysis.domain.EvaluationInputSnapshot inputSnapshot() {
		return new com.gitddo.analysis.domain.EvaluationInputSnapshot(
				1,
				1L,
				0L,
				"Backend Portfolio",
				EvaluationPurpose.PORTFOLIO_REVIEW,
				TargetLevel.ENTRY,
				Set.of(EvaluationArea.BACKEND),
				List.of(new com.gitddo.analysis.domain.EvaluationInputSnapshot.RepositorySnapshot(
						123L,
						"git-ddo/backend",
						"https://github.com/git-ddo/backend",
						"Java",
						"API를 구현했습니다.",
						"Backend",
						List.of()
				))
		);
	}

	private GithubTreeEntryPayload entry(String path, String sha, Long size) {
		return new GithubTreeEntryPayload(
				path,
				"100644",
				"blob",
				sha,
				size,
				"https://api.github.com/blob/" + sha
		);
	}

	private GithubBlobPayload blob(String sha, String content) {
		return new GithubBlobPayload(
				sha,
				(long) content.length(),
				"base64",
				Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8))
		);
	}
}

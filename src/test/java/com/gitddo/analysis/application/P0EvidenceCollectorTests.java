package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.EvidenceType;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P0EvidenceCollectorTests {

	@Test
	void collectsFixedSnapshotAndCreatesSanitizedEvidence() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		P0EvidenceCollector collector = new P0EvidenceCollector(
				client,
				new P0FileSelectionPolicy(),
				new P0TechnologyDetector()
		);
		when(client.fetchRepository("token", "git-ddo", "backend"))
				.thenReturn(repositoryPayload());
		when(client.fetchCommitSnapshot("token", "git-ddo", "backend", "main"))
				.thenReturn(new GithubCommitSnapshotPayload(
						"commit-sha",
						new GithubCommitSnapshotPayload.CommitPayload(
								new GithubCommitSnapshotPayload.TreePayload("tree-sha")
						)
				));
		when(client.fetchLanguages("token", "git-ddo", "backend"))
				.thenReturn(Map.of("Java", 10_000L, "SQL", 500L));
		when(client.fetchTree("token", "git-ddo", "backend", "tree-sha"))
				.thenReturn(new GithubTreePayload(
						"tree-sha",
						false,
						List.of(
								entry("README.md", "readme-sha", 40L),
								entry("build.gradle", "build-sha", 30L),
								entry("settings.gradle", "settings-sha", 10L),
								entry(".env", "env-sha", 20L),
								entry(".DS_Store", "ds-sha", 6L),
								entry(".idea/workspace.xml", "idea-sha", 80L),
								entry("Users/kimjunghyun/.zshrc", "zsh-sha", 10L),
								entry(".env.example", "env-example-sha", 12L),
								treeEntry("src"),
								treeEntry("src/test"),
								treeEntry("src/test/java"),
								entry("src/test/java/AppTests.java", "test-sha", 50L)
						)
				));
		when(client.fetchBlob("token", "git-ddo", "backend", "readme-sha"))
				.thenReturn(blob("readme-sha", "# Backend\napiKey=secret-value"));
		when(client.fetchBlob("token", "git-ddo", "backend", "build-sha"))
				.thenReturn(blob("build-sha", "plugins { id 'java' }"));
		when(client.fetchBlob("token", "git-ddo", "backend", "settings-sha"))
				.thenReturn(blob("settings-sha", "rootProject.name = 'backend'"));

		P0EvidenceSnapshot snapshot = collector.collect("token", inputSnapshot());

		assertThat(snapshot.repositories()).singleElement().satisfies(repository -> {
			assertThat(repository.snapshotSha()).isEqualTo("commit-sha");
			assertThat(repository.treeSha()).isEqualTo("tree-sha");
			assertThat(repository.languages()).containsEntry("Java", 10_000L);
		});
		assertThat(snapshot.evidence())
				.extracting(P0EvidenceSnapshot.Evidence::kind)
				.contains(
						EvidenceKind.REPOSITORY_METADATA,
						EvidenceKind.LANGUAGE_BREAKDOWN,
						EvidenceKind.FILE_TREE_SUMMARY,
						EvidenceKind.README,
						EvidenceKind.BUILD_MANIFEST,
						EvidenceKind.PROJECT_STRUCTURE,
						EvidenceKind.TECHNOLOGY_DETECTED
				);
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.README)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.content()).contains("apiKey=[REDACTED]");
					assertThat(evidence.snapshotSha()).isEqualTo("commit-sha");
				});
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.BUILD_MANIFEST)
				.hasSize(2);
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.TECHNOLOGY_DETECTED)
				.extracting(P0EvidenceSnapshot.Evidence::content)
				.contains("Gradle", "Java")
				.doesNotContain("Spring Boot");
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.TECHNOLOGY_DETECTED)
				.allSatisfy(evidence -> {
					assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.BACKEND_DERIVED);
					assertThat(evidence.sourceEvidenceRefs()).isNotEmpty();
					assertThat(evidence.path()).isNull();
				});

		String fileTreeSummaryId = snapshot.evidence().stream()
				.filter(evidence -> evidence.kind() == EvidenceKind.FILE_TREE_SUMMARY)
				.findFirst()
				.orElseThrow()
				.evidenceId();
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.FILE_TREE_SUMMARY)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.content()).contains("README.md");
					assertThat(evidence.content()).contains(".env.example");
					assertThat(evidence.content().lines()).noneMatch(line ->
							line.endsWith("\t.DS_Store")
									|| line.endsWith("\t.env")
									|| line.contains(".idea/")
									|| line.contains("Users/kimjunghyun"));
				});
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.PROJECT_STRUCTURE)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.content()).contains("testFileCount=1");
					assertThat(evidence.content()).contains("omittedNoiseEntryCount=4");
					assertThat(evidence.content()).contains("hasEnvFile=true");
					assertThat(evidence.content()).contains("hasEditorOrOsJunk=true");
					assertThat(evidence.sourceEvidenceRefs())
							.containsExactly(fileTreeSummaryId);
				});
		verify(client, never())
				.fetchBlob("token", "git-ddo", "backend", "env-sha");
	}

	@Test
	void keepsTechnologySourcesInsideTheSameRepository() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		P0EvidenceCollector collector = new P0EvidenceCollector(
				client,
				new P0FileSelectionPolicy(),
				new P0TechnologyDetector()
		);
		stubSpringGradleRepository(
				client,
				"git-ddo",
				"backend",
				111L,
				"commit-a",
				"tree-a",
				"build-a"
		);
		stubSpringGradleRepository(
				client,
				"congraduation-team",
				"congraduation-backend",
				222L,
				"commit-b",
				"tree-b",
				"build-b"
		);

		P0EvidenceSnapshot snapshot = collector.collect(
				"token",
				new EvaluationInputSnapshot(
						1,
						1L,
						0L,
						"Backend Portfolio",
						EvaluationPurpose.PORTFOLIO_REVIEW,
						TargetLevel.ENTRY,
						Set.of(EvaluationArea.BACKEND),
						"git-ddo-user",
						List.of(
								repositoryInput(111L, "git-ddo/backend"),
								repositoryInput(222L, "congraduation-team/congraduation-backend")
						)
				)
		);

		Map<String, P0EvidenceSnapshot.Evidence> evidenceById = snapshot.evidence().stream()
				.collect(Collectors.toMap(
						P0EvidenceSnapshot.Evidence::evidenceId,
						evidence -> evidence
				));
		List<P0EvidenceSnapshot.Evidence> detectedTechnologies = snapshot.evidence().stream()
				.filter(evidence -> evidence.kind() == EvidenceKind.TECHNOLOGY_DETECTED)
				.toList();

		assertThat(detectedTechnologies).isNotEmpty();
		assertThat(detectedTechnologies)
				.extracting(P0EvidenceSnapshot.Evidence::content)
				.contains("Gradle", "Spring Boot");
		assertThat(detectedTechnologies)
				.filteredOn(evidence -> "111".equals(evidence.repositoryId()))
				.extracting(P0EvidenceSnapshot.Evidence::content)
				.contains("Gradle", "Spring Boot");
		assertThat(detectedTechnologies)
				.filteredOn(evidence -> "222".equals(evidence.repositoryId()))
				.extracting(P0EvidenceSnapshot.Evidence::content)
				.contains("Gradle", "Spring Boot");
		assertThat(detectedTechnologies).allSatisfy(evidence -> {
			assertThat(evidence.sourceEvidenceRefs()).isNotEmpty();
			assertThat(evidence.sourceEvidenceRefs()).allSatisfy(sourceId -> {
				P0EvidenceSnapshot.Evidence source = evidenceById.get(sourceId);
				assertThat(source).isNotNull();
				assertThat(source.repositoryId()).isEqualTo(evidence.repositoryId());
			});
		});
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
				List.of(new EvaluationInputSnapshot.RepositorySnapshot(
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

	private EvaluationInputSnapshot.RepositorySnapshot repositoryInput(Long githubRepositoryId, String fullName) {
		return new EvaluationInputSnapshot.RepositorySnapshot(
				githubRepositoryId,
				fullName,
				"https://github.com/" + fullName,
				"Java",
				"API를 구현했습니다.",
				"Backend",
				List.of()
		);
	}

	private void stubSpringGradleRepository(
			GithubAnalysisClient client,
			String owner,
			String name,
			Long githubRepositoryId,
			String commitSha,
			String treeSha,
			String buildSha
	) {
		when(client.fetchRepository("token", owner, name))
				.thenReturn(new GithubRepositoryPayload(
						githubRepositoryId,
						name,
						owner + "/" + name,
						"Backend service",
						"https://github.com/" + owner + "/" + name,
						"Java",
						false,
						false,
						"main",
						1,
						0,
						Instant.parse("2026-08-17T00:00:00Z"),
						Instant.parse("2026-08-17T00:00:00Z")
				));
		when(client.fetchCommitSnapshot("token", owner, name, "main"))
				.thenReturn(new GithubCommitSnapshotPayload(
						commitSha,
						new GithubCommitSnapshotPayload.CommitPayload(
								new GithubCommitSnapshotPayload.TreePayload(treeSha)
						)
				));
		when(client.fetchLanguages("token", owner, name))
				.thenReturn(Map.of("Java", 10_000L));
		when(client.fetchTree("token", owner, name, treeSha))
				.thenReturn(new GithubTreePayload(
						treeSha,
						false,
						List.of(entry("build.gradle", buildSha, 80L))
				));
		when(client.fetchBlob("token", owner, name, buildSha))
				.thenReturn(blob(
						buildSha,
						"""
								plugins {
								  id 'java'
								  id 'org.springframework.boot'
								}
								implementation 'org.springframework.boot:spring-boot-starter-web'
								"""
				));
	}

	private GithubRepositoryPayload repositoryPayload() {
		return new GithubRepositoryPayload(
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

	private GithubTreeEntryPayload treeEntry(String path) {
		return new GithubTreeEntryPayload(
				path,
				"040000",
				"tree",
				path + "-tree-sha",
				null,
				"https://api.github.com/tree/" + path
		);
	}

	private GithubBlobPayload blob(String sha, String content) {
		String encoded = Base64.getEncoder().encodeToString(
				content.getBytes(StandardCharsets.UTF_8)
		);
		return new GithubBlobPayload(
				sha,
				(long) content.length(),
				"base64",
				encoded
		);
	}
}

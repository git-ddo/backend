package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvidenceKind;
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
				new P0FileSelectionPolicy()
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
						EvidenceKind.PROJECT_STRUCTURE
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

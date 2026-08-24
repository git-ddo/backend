package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.EvidenceType;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.github.client.GithubAnalysisClient;
import com.gitddo.github.client.GithubCommitDetailPayload;
import com.gitddo.github.client.GithubCommitListItemPayload;
import com.gitddo.github.client.GithubPullRequestFilePayload;
import com.gitddo.github.client.GithubPullRequestPayload;
import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.TargetLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P1EvidenceCollectorTests {

	@Test
	void appendsAuthorCommitsPullRequestsAndChangedFiles() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		when(client.fetchPullRequests("token", "git-ddo", "backend", 30))
				.thenReturn(List.of(
						pullRequest(12, "git-ddo-user", "Add auth filter", "def456"),
						pullRequest(13, "someone-else", "Docs", "zzz")
				));
		when(client.fetchPullRequestFiles("token", "git-ddo", "backend", 12, 41))
				.thenReturn(List.of(new GithubPullRequestFilePayload(
						"src/AuthFilter.java",
						"added",
						20,
						0
				)));
		when(client.fetchCommits("token", "git-ddo", "backend", "git-ddo-user", "commit-sha", 100))
				.thenReturn(List.of(commit("abc123", "Implement auth filter")));
		when(client.fetchCommitDetail("token", "git-ddo", "backend", "abc123"))
				.thenReturn(new GithubCommitDetailPayload(
						"abc123",
						new GithubCommitDetailPayload.StatsPayload(20, 1, 21),
						List.of(new GithubCommitDetailPayload.FilePayload(
								"src/AuthFilter.java",
								"added",
								20,
								1
						))
				));

		P0EvidenceSnapshot p0 = p0Snapshot();
		P0EvidenceSnapshot snapshot = new P1EvidenceCollector(client, new ActivityImpactPolicy())
				.collect("token", inputSnapshot(), p0);

		assertThat(snapshot.extractorVersion()).isEqualTo(P0EvidenceSnapshot.P1_EXTRACTOR_VERSION);
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.analysisDepth() == AnalysisDepth.P0)
				.hasSize(1);
		assertThat(snapshot.evidence())
				.extracting(P0EvidenceSnapshot.Evidence::kind)
				.contains(
						EvidenceKind.README,
						EvidenceKind.PULL_REQUEST,
						EvidenceKind.COMMIT_SUMMARY,
						EvidenceKind.CHANGED_FILES,
						EvidenceKind.ACTIVITY_SUMMARY
				);
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.PULL_REQUEST)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.GITHUB_ACTIVITY);
					assertThat(evidence.pullRequestNumber()).isEqualTo(12);
					assertThat(evidence.content()).contains("src/AuthFilter.java");
					assertThat(evidence.content()).doesNotContain("Docs");
				});
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.COMMIT_SUMMARY)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.commitSha()).isEqualTo("abc123");
					assertThat(evidence.content()).contains("Implement auth filter");
				});
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.CHANGED_FILES)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.content()).contains("src/AuthFilter.java");
					assertThat(evidence.sourceEvidenceRefs()).isNotEmpty();
				});
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.ACTIVITY_SUMMARY)
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.BACKEND_DERIVED);
					assertThat(evidence.content()).contains("listedCommitCount=1");
					assertThat(evidence.content()).contains("selection=SOURCE_IMPACT");
					assertThat(evidence.content()).contains("pullRequestCount=1");
					assertThat(evidence.sourceEvidenceRefs()).isNotEmpty();
				});
		verify(client, never()).fetchPullRequestFiles("token", "git-ddo", "backend", 13, 41);
	}

	@Test
	void prefersSourceImpactOverLockfileAndDocs() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		when(client.fetchPullRequests("token", "git-ddo", "backend", 30))
				.thenReturn(List.of(
						pullRequest(1, "git-ddo-user", "chore: bump lockfile", "aaa"),
						pullRequest(2, "git-ddo-user", "feat: billing domain", "bbb")
				));
		when(client.fetchPullRequestFiles("token", "git-ddo", "backend", 1, 41))
				.thenReturn(List.of(new GithubPullRequestFilePayload("package-lock.json", "modified", 9_000, 9_000)));
		when(client.fetchPullRequestFiles("token", "git-ddo", "backend", 2, 41))
				.thenReturn(List.of(new GithubPullRequestFilePayload(
						"src/main/java/com/gitddo/billing/domain/Invoice.java",
						"added",
						80,
						0
				)));
		when(client.fetchCommits("token", "git-ddo", "backend", "git-ddo-user", "commit-sha", 100))
				.thenReturn(List.of(
						commit("lock1", "chore: refresh lockfile"),
						commit("docs1", "docs: update readme"),
						commit("core1", "feat: extract billing domain")
				));
		when(client.fetchCommitDetail("token", "git-ddo", "backend", "lock1"))
				.thenReturn(detail("lock1", "package-lock.json", 9_000, 9_000));
		when(client.fetchCommitDetail("token", "git-ddo", "backend", "docs1"))
				.thenReturn(detail("docs1", "README.md", 300, 20));
		when(client.fetchCommitDetail("token", "git-ddo", "backend", "core1"))
				.thenReturn(detail(
						"core1",
						"src/main/java/com/gitddo/billing/domain/Invoice.java",
						40,
						8
				));

		P0EvidenceSnapshot snapshot = new P1EvidenceCollector(client, new ActivityImpactPolicy())
				.collect("token", inputSnapshot(), p0Snapshot());

		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.COMMIT_SUMMARY)
				.extracting(P0EvidenceSnapshot.Evidence::commitSha)
				.containsExactly("core1", "docs1");
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.PULL_REQUEST)
				.extracting(P0EvidenceSnapshot.Evidence::pullRequestNumber)
				.containsExactly(2);
	}

	@Test
	void skipsActivityWhenGithubLoginIsMissing() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		P0EvidenceSnapshot p0 = p0Snapshot();
		EvaluationInputSnapshot input = new EvaluationInputSnapshot(
				1,
				1L,
				0L,
				"Backend Portfolio",
				EvaluationPurpose.PORTFOLIO_REVIEW,
				TargetLevel.ENTRY,
				Set.of(EvaluationArea.BACKEND),
				null,
				List.of()
		);

		P0EvidenceSnapshot snapshot = new P1EvidenceCollector(client, new ActivityImpactPolicy())
				.collect("token", input, p0);

		assertThat(snapshot.evidence()).hasSize(1);
		assertThat(snapshot.warnings())
				.extracting(P0EvidenceSnapshot.Warning::code)
				.contains("MISSING_GITHUB_LOGIN");
		verify(client, never()).fetchCommits(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyInt()
		);
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

	private P0EvidenceSnapshot p0Snapshot() {
		return new P0EvidenceSnapshot(
				1,
				P0EvidenceSnapshot.CURRENT_EXTRACTOR_VERSION,
				Instant.parse("2026-08-19T00:00:00Z"),
				List.of(new P0EvidenceSnapshot.RepositorySnapshot(
						"123",
						"git-ddo/backend",
						"commit-sha",
						"tree-sha",
						Map.of("Java", 10L),
						1,
						false
				)),
				List.of(new P0EvidenceSnapshot.Evidence(
						"ev_001",
						EvidenceType.GITHUB_STATIC,
						EvidenceKind.README,
						AnalysisDepth.P0,
						"123",
						"commit-sha",
						"README.md",
						null,
						null,
						null,
						null,
						"# Backend",
						"hash",
						false,
						List.of()
				)),
				List.of()
		);
	}

	private GithubPullRequestPayload pullRequest(int number, String login, String title, String headSha) {
		return new GithubPullRequestPayload(
				number,
				title,
				"closed",
				new GithubPullRequestPayload.UserPayload(login),
				new GithubPullRequestPayload.HeadPayload(headSha),
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-02T00:00:00Z")
		);
	}

	private GithubCommitDetailPayload detail(String sha, String path, int additions, int deletions) {
		return new GithubCommitDetailPayload(
				sha,
				new GithubCommitDetailPayload.StatsPayload(additions, deletions, additions + deletions),
				List.of(new GithubCommitDetailPayload.FilePayload(path, "modified", additions, deletions))
		);
	}

	@Test
	void excludesMergeCommitsFromSelectionAndDetailLookup() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		when(client.fetchPullRequests("token", "git-ddo", "backend", 30)).thenReturn(List.of());
		when(client.fetchCommits("token", "git-ddo", "backend", "git-ddo-user", "commit-sha", 100))
				.thenReturn(List.of(
						mergeCommit("merge1", "Merge pull request #9 from git-ddo/login"),
						commit("feat1", "feat: 로그인 구현")
				));
		when(client.fetchCommitDetail("token", "git-ddo", "backend", "feat1"))
				.thenReturn(detail("feat1", "src/AuthService.java", 40, 4));

		P0EvidenceSnapshot snapshot = new P1EvidenceCollector(client, new ActivityImpactPolicy())
				.collect("token", inputSnapshot(), p0Snapshot());

		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.COMMIT_SUMMARY)
				.extracting(P0EvidenceSnapshot.Evidence::commitSha)
				.containsExactly("feat1")
				.doesNotContain("merge1");
		assertThat(snapshot.warnings())
				.extracting(P0EvidenceSnapshot.Warning::code)
				.contains("MERGE_COMMITS_EXCLUDED");
		verify(client, never()).fetchCommitDetail("token", "git-ddo", "backend", "merge1");
	}

	private GithubCommitListItemPayload commit(String sha, String message) {
		return new GithubCommitListItemPayload(
				sha,
				new GithubCommitListItemPayload.CommitPayload(
						message,
						new GithubCommitListItemPayload.GitUserPayload(
								"Kim",
								Instant.parse("2026-08-01T12:00:00Z")
						)
				),
				new GithubCommitListItemPayload.UserPayload("git-ddo-user"),
				List.of(new GithubCommitListItemPayload.ParentPayload("parent"))
		);
	}

	private GithubCommitListItemPayload mergeCommit(String sha, String message) {
		return new GithubCommitListItemPayload(
				sha,
				new GithubCommitListItemPayload.CommitPayload(
						message,
						new GithubCommitListItemPayload.GitUserPayload(
								"Kim",
								Instant.parse("2026-08-01T12:00:00Z")
						)
				),
				new GithubCommitListItemPayload.UserPayload("git-ddo-user"),
				List.of(
						new GithubCommitListItemPayload.ParentPayload("left"),
						new GithubCommitListItemPayload.ParentPayload("right")
				)
		);
	}
}

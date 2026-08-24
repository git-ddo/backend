package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.EvidenceType;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.github.client.GithubAnalysisClient;
import com.gitddo.github.client.GithubFileContentPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P2EvidenceCollectorTests {

	@Test
	void appendsSnippetsFromP1TaggedSourceFiles() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		when(client.fetchFileAtRef(
				"token",
				"git-ddo",
				"backend",
				"src/main/java/com/gitddo/billing/domain/Invoice.java",
				"def456"
		)).thenReturn(file(
				"src/main/java/com/gitddo/billing/domain/Invoice.java",
				"""
						package com.gitddo.billing.domain;
						
						public class Invoice {
							public int total() {
								return 100;
							}
						}
						"""
		));
		when(client.fetchFileAtRef(
				"token",
				"git-ddo",
				"backend",
				"src/AuthFilter.java",
				"abc123"
		)).thenReturn(file(
				"src/AuthFilter.java",
				"""
						package com.gitddo.auth;
						
						public class AuthFilter {
							public boolean matches(String path) {
								return true;
							}
						}
						"""
		));

		P0EvidenceSnapshot snapshot = new P2EvidenceCollector(
				client,
				new ActivityImpactPolicy(),
				new CodeSnippetPolicy()
		).collect("token", p1Snapshot());

		assertThat(snapshot.extractorVersion()).isEqualTo(P0EvidenceSnapshot.P2_EXTRACTOR_VERSION);
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.CODE_SNIPPET)
				.hasSize(2)
				.allMatch(evidence -> evidence.evidenceType() == EvidenceType.CODE_EVIDENCE)
				.allMatch(evidence -> evidence.analysisDepth() == AnalysisDepth.P2)
				.allMatch(evidence -> evidence.startLine() != null && evidence.endLine() >= evidence.startLine())
				.allMatch(evidence -> !evidence.sourceEvidenceRefs().isEmpty());
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.CODE_SNIPPET)
				.extracting(P0EvidenceSnapshot.Evidence::path)
				.containsExactly(
						"src/main/java/com/gitddo/billing/domain/Invoice.java",
						"src/AuthFilter.java"
				);
		assertThat(snapshot.evidence())
				.filteredOn(evidence -> "src/main/java/com/gitddo/billing/domain/Invoice.java".equals(evidence.path()))
				.singleElement()
				.satisfies(evidence -> {
					assertThat(evidence.commitSha()).isEqualTo("def456");
					assertThat(evidence.pullRequestNumber()).isEqualTo(12);
					assertThat(evidence.content()).contains("public class Invoice");
					assertThat(evidence.content()).doesNotContain("package com.gitddo.billing.domain");
					assertThat(evidence.sourceEvidenceRefs()).contains("ev_004");
				});
		verify(client, never()).fetchFileAtRef(
				"token",
				"git-ddo",
				"backend",
				"package-lock.json",
				"abc123"
		);
	}

	@Test
	void keepsOneSnippetWhenSamePathAndContentAppearTwice() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		String source = """
				package com.gitddo.auth;
				
				public class AuthFilter {
					public boolean matches(String path) {
						return true;
					}
				}
				""";
		when(client.fetchFileAtRef("token", "git-ddo", "backend", "src/AuthFilter.java", "pr-sha"))
				.thenReturn(file("src/AuthFilter.java", source));
		when(client.fetchFileAtRef("token", "git-ddo", "backend", "src/AuthFilter.java", "merge-sha"))
				.thenReturn(file("src/AuthFilter.java", source));

		P0EvidenceSnapshot p1 = new P0EvidenceSnapshot(
				1,
				P0EvidenceSnapshot.P1_EXTRACTOR_VERSION,
				Instant.parse("2026-08-23T00:00:00Z"),
				List.of(new P0EvidenceSnapshot.RepositorySnapshot(
						"123",
						"git-ddo/backend",
						"commit-sha",
						"tree-sha",
						Map.of("Java", 10L),
						1,
						false
				)),
				List.of(
						activity(
								"ev_001",
								EvidenceKind.PULL_REQUEST,
								"pr-sha",
								12,
								"""
										number=12
										headSha=pr-sha
										files:
										added	src/AuthFilter.java	+20/-1
										"""
						),
						activity(
								"ev_002",
								EvidenceKind.CHANGED_FILES,
								"merge-sha",
								null,
								"""
										sha=merge-sha
										files:
										added	src/AuthFilter.java	+20/-1
										"""
						)
				),
				List.of()
		);

		P0EvidenceSnapshot snapshot = new P2EvidenceCollector(
				client,
				new ActivityImpactPolicy(),
				new CodeSnippetPolicy()
		).collect("token", p1);

		assertThat(snapshot.evidence())
				.filteredOn(evidence -> evidence.kind() == EvidenceKind.CODE_SNIPPET)
				.hasSize(1)
				.first()
				.satisfies(evidence -> assertThat(evidence.path()).isEqualTo("src/AuthFilter.java"));
	}

	@Test
	void doesNotFetchNoiseFilesAndWarnsWhenNoSnippetIsMade() {
		GithubAnalysisClient client = mock(GithubAnalysisClient.class);
		P0EvidenceSnapshot p1 = new P0EvidenceSnapshot(
				1,
				P0EvidenceSnapshot.P1_EXTRACTOR_VERSION,
				Instant.parse("2026-08-23T00:00:00Z"),
				List.of(new P0EvidenceSnapshot.RepositorySnapshot(
						"123",
						"git-ddo/backend",
						"commit-sha",
						"tree-sha",
						Map.of("Java", 10L),
						1,
						false
				)),
				List.of(
						activity("ev_001", EvidenceKind.COMMIT_SUMMARY, "abc123", null, "sha=abc123"),
						activity(
								"ev_002",
								EvidenceKind.CHANGED_FILES,
								"abc123",
								null,
								"""
										sha=abc123
										files:
										modified	package-lock.json	+9000/-9000
										"""
						)
				),
				List.of()
		);

		P0EvidenceSnapshot snapshot = new P2EvidenceCollector(
				client,
				new ActivityImpactPolicy(),
				new CodeSnippetPolicy()
		).collect("token", p1);

		assertThat(snapshot.evidence())
				.noneMatch(evidence -> evidence.kind() == EvidenceKind.CODE_SNIPPET);
		assertThat(snapshot.warnings())
				.extracting(P0EvidenceSnapshot.Warning::code)
				.contains("MISSING_CODE_SNIPPET");
		verify(client, never()).fetchFileAtRef(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		);
	}

	private P0EvidenceSnapshot p1Snapshot() {
		return new P0EvidenceSnapshot(
				1,
				P0EvidenceSnapshot.P1_EXTRACTOR_VERSION,
				Instant.parse("2026-08-23T00:00:00Z"),
				List.of(new P0EvidenceSnapshot.RepositorySnapshot(
						"123",
						"git-ddo/backend",
						"commit-sha",
						"tree-sha",
						Map.of("Java", 10L),
						1,
						false
				)),
				List.of(
						new P0EvidenceSnapshot.Evidence(
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
						),
						activity("ev_002", EvidenceKind.COMMIT_SUMMARY, "abc123", null, "sha=abc123"),
						activity(
								"ev_003",
								EvidenceKind.CHANGED_FILES,
								"abc123",
								null,
								"""
										sha=abc123
										files:
										added	src/AuthFilter.java	+20/-1
										added	package-lock.json	+9000/-9000
										"""
						),
						activity(
								"ev_004",
								EvidenceKind.PULL_REQUEST,
								"def456",
								12,
								"""
										number=12
										headSha=def456
										files:
										added	src/main/java/com/gitddo/billing/domain/Invoice.java	+80/-0
										"""
						)
				),
				List.of()
		);
	}

	private P0EvidenceSnapshot.Evidence activity(
			String evidenceId,
			EvidenceKind kind,
			String commitSha,
			Integer pullRequestNumber,
			String content
	) {
		return new P0EvidenceSnapshot.Evidence(
				evidenceId,
				EvidenceType.GITHUB_ACTIVITY,
				kind,
				AnalysisDepth.P1,
				"123",
				"commit-sha",
				null,
				commitSha,
				pullRequestNumber,
				null,
				null,
				content,
				"hash-" + evidenceId,
				false,
				kind == EvidenceKind.CHANGED_FILES ? List.of("ev_002") : List.of()
		);
	}

	private GithubFileContentPayload file(String path, String content) {
		return new GithubFileContentPayload(
				"file",
				"base64",
				(long) content.length(),
				path,
				"blob-sha",
				Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8))
		);
	}
}

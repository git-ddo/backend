package com.gitddo.analysis.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record P0EvidenceSnapshot(
		int schemaVersion,
		String extractorVersion,
		Instant collectedAt,
		List<RepositorySnapshot> repositories,
		List<Evidence> evidence,
		List<Warning> warnings
) {

	public static final int CURRENT_SCHEMA_VERSION = 1;
	public static final String CURRENT_EXTRACTOR_VERSION = "p0-collector-1.0";

	public P0EvidenceSnapshot {
		repositories = List.copyOf(repositories);
		evidence = List.copyOf(evidence);
		warnings = List.copyOf(warnings);
	}

	public record RepositorySnapshot(
			String repositoryId,
			String fullName,
			String snapshotSha,
			String treeSha,
			Map<String, Long> languages,
			int treeEntryCount,
			boolean treeTruncated
	) {

		public RepositorySnapshot {
			languages = Map.copyOf(languages);
		}
	}

	public record Evidence(
			String evidenceId,
			EvidenceType evidenceType,
			P0EvidenceKind kind,
			String repositoryId,
			String snapshotSha,
			String path,
			String content,
			String contentHash,
			boolean truncated,
			List<String> sourceEvidenceRefs
	) {

		public Evidence {
			sourceEvidenceRefs = List.copyOf(sourceEvidenceRefs);
		}
	}

	public record Warning(
			String code,
			String repositoryId,
			String path,
			String message
	) {
	}
}

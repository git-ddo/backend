package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AnalysisDepth;
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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class P0EvidenceCollector {

	private static final int MAX_FETCH_BYTES = 100_000;
	private static final Pattern ASSIGNED_SECRET = Pattern.compile(
			"(?i)(password|secret|token|api[_-]?key)(\\s*[:=]\\s*)([^\\s\"']+)"
	);
	private static final Pattern GITHUB_TOKEN = Pattern.compile(
			"\\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}\\b"
	);
	private static final Pattern AWS_ACCESS_KEY = Pattern.compile(
			"\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"
	);

	private final GithubAnalysisClient githubAnalysisClient;
	private final P0FileSelectionPolicy fileSelectionPolicy;

	public P0EvidenceCollector(
			GithubAnalysisClient githubAnalysisClient,
			P0FileSelectionPolicy fileSelectionPolicy
	) {
		this.githubAnalysisClient = githubAnalysisClient;
		this.fileSelectionPolicy = fileSelectionPolicy;
	}

	public P0EvidenceSnapshot collect(
			String accessToken,
			EvaluationInputSnapshot inputSnapshot
	) {
		List<P0EvidenceSnapshot.RepositorySnapshot> repositories = new ArrayList<>();
		List<EvidenceCandidate> candidates = new ArrayList<>();
		List<P0EvidenceSnapshot.Warning> warnings = new ArrayList<>();

		for (int order = 0; order < inputSnapshot.repositories().size(); order++) {
			collectRepository(
					accessToken,
					inputSnapshot.repositories().get(order),
					order,
					repositories,
					candidates,
					warnings
			);
		}

		List<P0EvidenceSnapshot.Evidence> evidence = assignEvidenceIds(candidates);
		return new P0EvidenceSnapshot(
				P0EvidenceSnapshot.CURRENT_SCHEMA_VERSION,
				P0EvidenceSnapshot.CURRENT_EXTRACTOR_VERSION,
				Instant.now(),
				repositories,
				evidence,
				warnings
		);
	}

	private void collectRepository(
			String accessToken,
			EvaluationInputSnapshot.RepositorySnapshot target,
			int repositoryOrder,
			List<P0EvidenceSnapshot.RepositorySnapshot> repositories,
			List<EvidenceCandidate> candidates,
			List<P0EvidenceSnapshot.Warning> warnings
	) {
		RepositoryName repositoryName = RepositoryName.parse(target.fullName());
		String repositoryId = String.valueOf(target.githubRepositoryId());
		GithubRepositoryPayload repository = githubAnalysisClient.fetchRepository(
				accessToken,
				repositoryName.owner(),
				repositoryName.name()
		);
		GithubCommitSnapshotPayload commit = githubAnalysisClient.fetchCommitSnapshot(
				accessToken,
				repositoryName.owner(),
				repositoryName.name(),
				requireText(repository.defaultBranch(), "GitHub 기본 브랜치가 없습니다.")
		);
		String snapshotSha = requireText(commit.sha(), "GitHub Snapshot SHA가 없습니다.");
		String treeSha = requireText(commit.treeSha(), "GitHub Tree SHA가 없습니다.");
		Map<String, Long> languages = githubAnalysisClient.fetchLanguages(
				accessToken,
				repositoryName.owner(),
				repositoryName.name()
		);
		GithubTreePayload tree = githubAnalysisClient.fetchTree(
				accessToken,
				repositoryName.owner(),
				repositoryName.name(),
				treeSha
		);

		repositories.add(new P0EvidenceSnapshot.RepositorySnapshot(
				repositoryId,
				repository.fullName(),
				snapshotSha,
				treeSha,
				languages,
				tree.tree().size(),
				tree.truncated()
		));
		candidates.add(candidate(
				repositoryOrder,
				EvidenceType.GITHUB_STATIC,
				EvidenceKind.REPOSITORY_METADATA,
				repositoryId,
				snapshotSha,
				null,
				repositoryMetadata(repository),
				false,
				null
		));
		candidates.add(candidate(
				repositoryOrder,
				EvidenceType.GITHUB_STATIC,
				EvidenceKind.LANGUAGE_BREAKDOWN,
				repositoryId,
				snapshotSha,
				null,
				languageSummary(languages),
				false,
				null
		));

		List<GithubTreeEntryPayload> sortedEntries = tree.tree().stream()
				.sorted(Comparator.comparing(GithubTreeEntryPayload::path))
				.toList();
		boolean limitedTree = sortedEntries.size() > P0FileSelectionPolicy.MAX_TREE_ENTRIES;
		String treeSummary = sortedEntries.stream()
				.limit(P0FileSelectionPolicy.MAX_TREE_ENTRIES)
				.map(entry -> entry.type() + "\t" + entry.path())
				.reduce((left, right) -> left + "\n" + right)
				.orElse("");
		boolean treeTruncated = tree.truncated() || limitedTree;
		EvidenceCandidate fileTreeSummaryCandidate = candidate(
				repositoryOrder,
				EvidenceType.GITHUB_STATIC,
				EvidenceKind.FILE_TREE_SUMMARY,
				repositoryId,
				snapshotSha,
				null,
				treeSummary,
				treeTruncated,
				null
		);
		candidates.add(fileTreeSummaryCandidate);
		if (tree.truncated()) {
			warnings.add(warning(
					"GITHUB_TREE_TRUNCATED",
					repositoryId,
					null,
					"GitHub가 파일 트리 일부만 반환했습니다."
			));
		}
		if (limitedTree) {
			warnings.add(warning(
					"FILE_TREE_LIMIT_EXCEEDED",
					repositoryId,
					null,
					"파일 트리는 최대 10,000개까지만 Evidence에 포함했습니다."
			));
		}

		collectSelectedFiles(
				accessToken,
				repositoryName,
				repositoryId,
				snapshotSha,
				repositoryOrder,
				sortedEntries,
				candidates,
				warnings
		);
		candidates.add(candidate(
				repositoryOrder,
				EvidenceType.BACKEND_DERIVED,
				EvidenceKind.PROJECT_STRUCTURE,
				repositoryId,
				snapshotSha,
				null,
				projectStructure(sortedEntries),
				false,
				fileTreeSummaryCandidate
		));
	}

	private void collectSelectedFiles(
			String accessToken,
			RepositoryName repositoryName,
			String repositoryId,
			String snapshotSha,
			int repositoryOrder,
			List<GithubTreeEntryPayload> entries,
			List<EvidenceCandidate> candidates,
			List<P0EvidenceSnapshot.Warning> warnings
	) {
		List<SelectedFile> selectedFiles = entries.stream()
				.filter(GithubTreeEntryPayload::isBlob)
				.map(entry -> fileSelectionPolicy.classify(entry.path())
						.map(kind -> new SelectedFile(entry, kind)))
				.flatMap(Optional::stream)
				.sorted(Comparator
						.comparing(SelectedFile::kind)
						.thenComparing(selected -> selected.entry().path()))
				.toList();

		if (selectedFiles.size() > P0FileSelectionPolicy.MAX_SELECTED_FILES) {
			warnings.add(warning(
					"SELECTED_FILE_LIMIT_EXCEEDED",
					repositoryId,
					null,
					"분석 파일은 저장소당 최대 10개까지만 수집했습니다."
			));
		}

		selectedFiles.stream()
				.limit(P0FileSelectionPolicy.MAX_SELECTED_FILES)
				.forEach(selected -> collectFile(
						accessToken,
						repositoryName,
						repositoryId,
						snapshotSha,
						repositoryOrder,
						selected,
						candidates,
						warnings
				));
	}

	private void collectFile(
			String accessToken,
			RepositoryName repositoryName,
			String repositoryId,
			String snapshotSha,
			int repositoryOrder,
			SelectedFile selected,
			List<EvidenceCandidate> candidates,
			List<P0EvidenceSnapshot.Warning> warnings
	) {
		GithubTreeEntryPayload entry = selected.entry();
		if (entry.size() != null && entry.size() > MAX_FETCH_BYTES) {
			warnings.add(warning(
					"FILE_TOO_LARGE",
					repositoryId,
					entry.path(),
					"100KB를 초과한 파일은 가져오지 않았습니다."
			));
			return;
		}

		GithubBlobPayload blob = githubAnalysisClient.fetchBlob(
				accessToken,
				repositoryName.owner(),
				repositoryName.name(),
				entry.sha()
		);
		byte[] decoded;
		try {
			decoded = Base64.getMimeDecoder().decode(blob.content());
		} catch (IllegalArgumentException exception) {
			warnings.add(warning(
					"UNSUPPORTED_FILE_ENCODING",
					repositoryId,
					entry.path(),
					"Base64 형식이 아닌 파일은 가져오지 않았습니다."
			));
			return;
		}
		if (containsNullByte(decoded)) {
			warnings.add(warning(
					"BINARY_FILE_SKIPPED",
					repositoryId,
					entry.path(),
					"바이너리 파일은 Evidence에서 제외했습니다."
			));
			return;
		}

		int limit = fileSelectionPolicy.maxBytes(selected.kind());
		boolean truncated = decoded.length > limit;
		int includedLength = Math.min(decoded.length, limit);
		String content = new String(decoded, 0, includedLength, StandardCharsets.UTF_8);
		content = redactSecrets(content);
		if (truncated) {
			warnings.add(warning(
					"FILE_CONTENT_TRUNCATED",
					repositoryId,
					entry.path(),
					"파일 크기 제한으로 내용 일부만 Evidence에 포함했습니다."
			));
		}
		candidates.add(candidate(
				repositoryOrder,
				EvidenceType.GITHUB_STATIC,
				selected.kind(),
				repositoryId,
				snapshotSha,
				entry.path(),
				content,
				truncated,
				null
		));
	}

	private List<P0EvidenceSnapshot.Evidence> assignEvidenceIds(
			List<EvidenceCandidate> candidates
	) {
		List<EvidenceCandidate> sorted = candidates.stream()
				.sorted(Comparator
						.comparingInt(EvidenceCandidate::repositoryOrder)
						.thenComparing(EvidenceCandidate::kind)
						.thenComparing(candidate ->
								candidate.path() == null ? "" : candidate.path()))
				.toList();
		Map<EvidenceCandidate, String> evidenceIdByCandidate = new IdentityHashMap<>();
		for (int index = 0; index < sorted.size(); index++) {
			evidenceIdByCandidate.put(sorted.get(index), "ev_%03d".formatted(index + 1));
		}

		List<P0EvidenceSnapshot.Evidence> evidence = new ArrayList<>();
		for (int index = 0; index < sorted.size(); index++) {
			EvidenceCandidate candidate = sorted.get(index);
			List<String> sourceRefs = candidate.derivedFrom() == null
					? List.of()
					: List.of(evidenceIdByCandidate.get(candidate.derivedFrom()));
			evidence.add(new P0EvidenceSnapshot.Evidence(
					"ev_%03d".formatted(index + 1),
					candidate.evidenceType(),
					candidate.kind(),
					AnalysisDepth.P0,
					candidate.repositoryId(),
					candidate.snapshotSha(),
					candidate.path(),
					null,
					null,
					candidate.content(),
					sha256(candidate.content()),
					candidate.truncated(),
					sourceRefs
			));
		}
		return List.copyOf(evidence);
	}

	private String repositoryMetadata(GithubRepositoryPayload repository) {
		return """
				fullName=%s
				description=%s
				defaultBranch=%s
				primaryLanguage=%s
				stars=%d
				forks=%d
				fork=%s
				archived=%s
				pushedAt=%s
				updatedAt=%s
				""".formatted(
				repository.fullName(),
				nullToEmpty(repository.description()),
				nullToEmpty(repository.defaultBranch()),
				nullToEmpty(repository.language()),
				repository.stargazersCount(),
				repository.forksCount(),
				repository.fork(),
				repository.archived(),
				repository.pushedAt(),
				repository.updatedAt()
		).strip();
	}

	private String languageSummary(Map<String, Long> languages) {
		return languages.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.reduce((left, right) -> left + "\n" + right)
				.orElse("");
	}

	private String projectStructure(List<GithubTreeEntryPayload> entries) {
		List<String> paths = entries.stream()
				.filter(GithubTreeEntryPayload::isBlob)
				.map(GithubTreeEntryPayload::path)
				.map(path -> path.toLowerCase(Locale.ROOT))
				.toList();
		long testFileCount = paths.stream()
				.filter(path -> path.startsWith("src/test/")
						|| path.contains("/test/")
						|| path.contains("/tests/"))
				.count();
		long migrationFileCount = paths.stream()
				.filter(path -> path.contains("/migration/")
						|| path.contains("/migrations/"))
				.count();
		boolean hasCi = paths.stream()
				.anyMatch(path -> path.startsWith(".github/workflows/"));
		boolean hasDocker = paths.stream()
				.anyMatch(path -> path.endsWith("dockerfile")
						|| path.contains("docker-compose")
						|| path.contains("compose."));
		return """
				testFileCount=%d
				migrationFileCount=%d
				hasCi=%s
				hasDocker=%s
				""".formatted(
				testFileCount,
				migrationFileCount,
				hasCi,
				hasDocker
		).strip();
	}

	private String redactSecrets(String content) {
		String redacted = ASSIGNED_SECRET.matcher(content)
				.replaceAll("$1$2[REDACTED]");
		redacted = GITHUB_TOKEN.matcher(redacted).replaceAll("[REDACTED_GITHUB_TOKEN]");
		return AWS_ACCESS_KEY.matcher(redacted).replaceAll("[REDACTED_AWS_ACCESS_KEY]");
	}

	private boolean containsNullByte(byte[] value) {
		for (byte current : value) {
			if (current == 0) {
				return true;
			}
		}
		return false;
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 해시 알고리즘을 사용할 수 없습니다.", exception);
		}
	}

	private EvidenceCandidate candidate(
			int repositoryOrder,
			EvidenceType evidenceType,
			EvidenceKind kind,
			String repositoryId,
			String snapshotSha,
			String path,
			String content,
			boolean truncated,
			EvidenceCandidate derivedFrom
	) {
		return new EvidenceCandidate(
				repositoryOrder,
				evidenceType,
				kind,
				repositoryId,
				snapshotSha,
				path,
				content,
				truncated,
				derivedFrom
		);
	}

	private P0EvidenceSnapshot.Warning warning(
			String code,
			String repositoryId,
			String path,
			String message
	) {
		return new P0EvidenceSnapshot.Warning(code, repositoryId, path, message);
	}

	private String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(message);
		}
		return value;
	}

	private String nullToEmpty(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private record RepositoryName(String owner, String name) {

		private static RepositoryName parse(String fullName) {
			String[] parts = fullName == null ? new String[0] : fullName.split("/", 2);
			if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
				throw new IllegalArgumentException("GitHub 저장소 fullName 형식이 올바르지 않습니다.");
			}
			return new RepositoryName(parts[0], parts[1]);
		}
	}

	private record SelectedFile(
			GithubTreeEntryPayload entry,
			EvidenceKind kind
	) {
	}

	private record EvidenceCandidate(
			int repositoryOrder,
			EvidenceType evidenceType,
			EvidenceKind kind,
			String repositoryId,
			String snapshotSha,
			String path,
			String content,
			boolean truncated,
			EvidenceCandidate derivedFrom
	) {
	}
}

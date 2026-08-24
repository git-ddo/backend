package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.EvidenceType;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import com.gitddo.github.client.GithubAnalysisClient;
import com.gitddo.github.client.GithubApiException;
import com.gitddo.github.client.GithubFileContentPayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class P2EvidenceCollector {

	private static final Pattern FILE_LINE = Pattern.compile(
			"^(\\S+)\\t([^\\t]+)\\t\\+(\\d+)/-(\\d+)$"
	);

	private final GithubAnalysisClient githubAnalysisClient;
	private final ActivityImpactPolicy activityImpactPolicy;
	private final CodeSnippetPolicy codeSnippetPolicy;
	private final CurrentPathResolver currentPathResolver = new CurrentPathResolver();

	public P2EvidenceCollector(
			GithubAnalysisClient githubAnalysisClient,
			ActivityImpactPolicy activityImpactPolicy,
			CodeSnippetPolicy codeSnippetPolicy
	) {
		this.githubAnalysisClient = githubAnalysisClient;
		this.activityImpactPolicy = activityImpactPolicy;
		this.codeSnippetPolicy = codeSnippetPolicy;
	}

	public P0EvidenceSnapshot collect(String accessToken, P0EvidenceSnapshot p1Snapshot) {
		List<P0EvidenceSnapshot.Evidence> evidence = new ArrayList<>(p1Snapshot.evidence());
		List<P0EvidenceSnapshot.Warning> warnings = new ArrayList<>(p1Snapshot.warnings());
		int nextId = evidence.size() + 1;
		boolean collectedAny = false;
		for (P0EvidenceSnapshot.RepositorySnapshot repository : p1Snapshot.repositories()) {
			int before = evidence.size();
			nextId = collectRepository(accessToken, repository, evidence, warnings, nextId);
			collectedAny = collectedAny || evidence.size() > before;
		}
		if (!collectedAny && hasActivityTags(p1Snapshot)) {
			warnings.add(new P0EvidenceSnapshot.Warning(
					"MISSING_CODE_SNIPPET",
					null,
					null,
					"P1 활동 태그는 있으나 선별 코드 조각을 만들지 못했습니다."
			));
		}
		return new P0EvidenceSnapshot(
				p1Snapshot.schemaVersion(),
				P0EvidenceSnapshot.P2_EXTRACTOR_VERSION,
				p1Snapshot.collectedAt(),
				p1Snapshot.repositories(),
				evidence,
				warnings
		);
	}

	private int collectRepository(
			String accessToken,
			P0EvidenceSnapshot.RepositorySnapshot repository,
			List<P0EvidenceSnapshot.Evidence> evidence,
			List<P0EvidenceSnapshot.Warning> warnings,
			int nextId
	) {
		CurrentTreeIndex currentTree = CurrentTreeIndex.from(evidence, repository.repositoryId());
		List<FileCandidate> candidates = rankCandidates(
				evidence,
				repository,
				currentTree,
				warnings
		);
		if (candidates.isEmpty()) {
			return nextId;
		}
		RepositoryName repositoryName = RepositoryName.parse(repository.fullName());
		Set<String> seenSnippets = new HashSet<>();
		Map<String, Integer> snippetsByOrigin = new LinkedHashMap<>();
		int added = 0;
		for (FileCandidate candidate : candidates) {
			if (added >= CodeSnippetPolicy.MAX_SNIPPETS_PER_REPOSITORY) {
				break;
			}
			if (reachedOriginLimit(snippetsByOrigin, candidate.originSourceId())) {
				continue;
			}
			if (codeSnippetPolicy.isSecretPath(candidate.path())) {
				warnings.add(warning(
						"SECRET_FILE_SKIPPED",
						repository.repositoryId(),
						candidate.path(),
						"시크릿으로 보이는 파일은 코드 조각에서 제외했습니다."
				));
				continue;
			}
			DecodedFile decoded = fetchSource(
					accessToken,
					repositoryName,
					candidate,
					warnings,
					repository.repositoryId()
			);
			if (decoded == null) {
				continue;
			}
			if (codeSnippetPolicy.looksLikeSecret(decoded.text())) {
				warnings.add(warning(
						"SECRET_FILE_SKIPPED",
						repository.repositoryId(),
						candidate.path(),
						"시크릿 마커가 있는 파일은 코드 조각에서 제외했습니다."
				));
				continue;
			}
			CodeSnippetPolicy.Snippet snippet = codeSnippetPolicy.extract(decoded.text());
			if (snippet == null) {
				continue;
			}
			String snippetKey = normalizePath(candidate.path()) + ":" + sha256(snippet.text());
			if (!seenSnippets.add(snippetKey)) {
				continue;
			}
			evidence.add(new P0EvidenceSnapshot.Evidence(
					"ev_%03d".formatted(nextId++),
					EvidenceType.CODE_EVIDENCE,
					EvidenceKind.CODE_SNIPPET,
					AnalysisDepth.P2,
					repository.repositoryId(),
					repository.snapshotSha(),
					candidate.path(),
					candidate.commitSha(),
					candidate.pullRequestNumber(),
					snippet.startLine(),
					snippet.endLine(),
					snippet.text(),
					sha256(snippet.text()),
					snippet.truncated(),
					candidate.sourceEvidenceIds()
			));
			countOrigin(snippetsByOrigin, candidate.originSourceId());
			added++;
		}
		return nextId;
	}

	private List<FileCandidate> rankCandidates(
			List<P0EvidenceSnapshot.Evidence> evidence,
			P0EvidenceSnapshot.RepositorySnapshot repository,
			CurrentTreeIndex currentTree,
			List<P0EvidenceSnapshot.Warning> warnings
	) {
		LinkedHashMap<String, AccumulableCandidate> unique = new LinkedHashMap<>();
		LinkedHashMap<String, String> unresolved = new LinkedHashMap<>();
		LinkedHashMap<String, String> remapped = new LinkedHashMap<>();
		for (P0EvidenceSnapshot.Evidence item : evidence) {
			if (!repository.repositoryId().equals(item.repositoryId()) || item.analysisDepth() != AnalysisDepth.P1) {
				continue;
			}
			if (item.kind() == EvidenceKind.CHANGED_FILES) {
				addFiles(unique, unresolved, remapped, currentTree, repository, item, null, sourceId(item));
			}
			if (item.kind() == EvidenceKind.PULL_REQUEST) {
				addFiles(
						unique,
						unresolved,
						remapped,
						currentTree,
						repository,
						item,
						item.pullRequestNumber(),
						item.evidenceId()
				);
			}
		}
		if (!remapped.isEmpty()) {
			warnings.add(warning(
					"PATH_RESOLVED_TO_CURRENT",
					repository.repositoryId(),
					null,
					"P1 경로 " + remapped.size() + "개를 현재 트리 파일로 재해석했습니다."
			));
		}
		for (String originalPath : unresolved.keySet()) {
			warnings.add(warning(
					"CODE_PATH_NOT_IN_SNAPSHOT",
					repository.repositoryId(),
					originalPath,
					"P1이 가리킨 경로의 현재 파일을 찾지 못해 코드 조각에서 제외했습니다."
			));
		}
		return unique.values().stream()
				.map(AccumulableCandidate::toFileCandidate)
				.sorted(Comparator.comparingDouble(FileCandidate::score).reversed()
						.thenComparing(FileCandidate::path))
				.limit(CodeSnippetPolicy.MAX_FILE_CANDIDATES)
				.toList();
	}

	private void addFiles(
			LinkedHashMap<String, AccumulableCandidate> unique,
			LinkedHashMap<String, String> unresolved,
			LinkedHashMap<String, String> remapped,
			CurrentTreeIndex currentTree,
			P0EvidenceSnapshot.RepositorySnapshot repository,
			P0EvidenceSnapshot.Evidence item,
			Integer pullRequestNumber,
			String sourceEvidenceId
	) {
		if (sourceEvidenceId == null) {
			return;
		}
		boolean inFiles = false;
		for (String line : item.content() == null ? List.<String>of() : item.content().lines().toList()) {
			if (line.equals("files:")) {
				inFiles = true;
				continue;
			}
			if (!inFiles) {
				continue;
			}
			Matcher matcher = FILE_LINE.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String path = matcher.group(2);
			int additions = Integer.parseInt(matcher.group(3));
			int deletions = Integer.parseInt(matcher.group(4));
			double weight = activityImpactPolicy.pathWeight(path);
			if (weight <= 0) {
				continue;
			}
			CurrentPathResolver.ResolvedPath resolved = currentPathResolver.resolve(path, currentTree);
			if (!resolved.found()) {
				unresolved.putIfAbsent(path, path);
				continue;
			}
			if (resolved.remapped()) {
				remapped.putIfAbsent(path, resolved.currentPath());
			}
			double score = weight * Math.max(1, additions + deletions);
			String key = normalizePath(resolved.currentPath());
			unique.computeIfAbsent(key, ignored -> new AccumulableCandidate(
					resolved.currentPath(),
					repository.snapshotSha(),
					pullRequestNumber,
					sourceEvidenceId
			)).add(score, sourceEvidenceId, pullRequestNumber);
		}
	}

	private DecodedFile fetchSource(
			String accessToken,
			RepositoryName repositoryName,
			FileCandidate candidate,
			List<P0EvidenceSnapshot.Warning> warnings,
			String repositoryId
	) {
		GithubFileContentPayload payload;
		try {
			payload = githubAnalysisClient.fetchFileAtRef(
					accessToken,
					repositoryName.owner(),
					repositoryName.name(),
					candidate.path(),
					candidate.commitSha()
			);
		} catch (GithubApiException exception) {
			if (exception.getGithubStatus() != null
					&& exception.getGithubStatus().value() == HttpStatus.NOT_FOUND.value()) {
				warnings.add(warning(
						"CODE_FILE_NOT_FOUND",
						repositoryId,
						candidate.path(),
						"P1이 가리킨 경로의 파일 내용을 찾지 못했습니다."
				));
				return null;
			}
			warnings.add(warning(
					"CODE_FILE_FETCH_FAILED",
					repositoryId,
					candidate.path(),
					"코드 조각 조회에 실패했습니다."
			));
			return null;
		}
		if (payload == null || !"file".equals(payload.type()) || payload.content() == null) {
			warnings.add(warning(
					"UNSUPPORTED_CODE_FILE",
					repositoryId,
					candidate.path(),
					"파일이 아닌 경로는 코드 조각에서 제외했습니다."
			));
			return null;
		}
		if (payload.size() != null && payload.size() > CodeSnippetPolicy.MAX_FILE_BYTES) {
			warnings.add(warning(
					"FILE_TOO_LARGE",
					repositoryId,
					candidate.path(),
					"코드 조각 한도를 넘는 파일은 가져오지 않았습니다."
			));
			return null;
		}
		byte[] decoded;
		try {
			decoded = Base64.getMimeDecoder().decode(payload.content());
		} catch (IllegalArgumentException exception) {
			warnings.add(warning(
					"UNSUPPORTED_FILE_ENCODING",
					repositoryId,
					candidate.path(),
					"Base64 형식이 아닌 파일은 가져오지 않았습니다."
			));
			return null;
		}
		if (containsNullByte(decoded)) {
			warnings.add(warning(
					"BINARY_FILE_SKIPPED",
					repositoryId,
					candidate.path(),
					"바이너리 파일은 코드 조각에서 제외했습니다."
			));
			return null;
		}
		return new DecodedFile(new String(decoded, StandardCharsets.UTF_8));
	}

	private boolean hasActivityTags(P0EvidenceSnapshot snapshot) {
		return snapshot.evidence().stream().anyMatch(item -> item.analysisDepth() == AnalysisDepth.P1);
	}

	private String normalizePath(String path) {
		if (path == null) {
			return "";
		}
		return path.replace('\\', '/').toLowerCase(Locale.ROOT);
	}

	private String sourceId(P0EvidenceSnapshot.Evidence item) {
		if (!item.sourceEvidenceRefs().isEmpty()) {
			return item.sourceEvidenceRefs().getFirst();
		}
		return item.evidenceId();
	}

	private boolean reachedOriginLimit(Map<String, Integer> snippetsByOrigin, String originSourceId) {
		if (originSourceId == null || originSourceId.isBlank()) {
			return false;
		}
		return snippetsByOrigin.getOrDefault(originSourceId, 0) >= CodeSnippetPolicy.MAX_SNIPPETS_PER_SOURCE;
	}

	private void countOrigin(Map<String, Integer> snippetsByOrigin, String originSourceId) {
		if (originSourceId == null || originSourceId.isBlank()) {
			return;
		}
		snippetsByOrigin.merge(originSourceId, 1, Integer::sum);
	}

	private boolean containsNullByte(byte[] bytes) {
		for (byte value : bytes) {
			if (value == 0) {
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

	private P0EvidenceSnapshot.Warning warning(
			String code,
			String repositoryId,
			String path,
			String message
	) {
		return new P0EvidenceSnapshot.Warning(code, repositoryId, path, message);
	}

	private record FileCandidate(
			String path,
			String commitSha,
			Integer pullRequestNumber,
			List<String> sourceEvidenceIds,
			String originSourceId,
			double score
	) {
	}

	private static final class AccumulableCandidate {
		private final String path;
		private final String commitSha;
		private Integer pullRequestNumber;
		private final LinkedHashSet<String> sourceEvidenceIds = new LinkedHashSet<>();
		private String originSourceId;
		private double score;
		private double bestContribution;

		private AccumulableCandidate(
				String path,
				String commitSha,
				Integer pullRequestNumber,
				String originSourceId
		) {
			this.path = path;
			this.commitSha = commitSha;
			this.pullRequestNumber = pullRequestNumber;
			this.originSourceId = originSourceId;
		}

		private void add(double contribution, String sourceEvidenceId, Integer pullRequestNumber) {
			score += contribution;
			sourceEvidenceIds.add(sourceEvidenceId);
			if (contribution > bestContribution) {
				bestContribution = contribution;
				originSourceId = sourceEvidenceId;
				if (pullRequestNumber != null) {
					this.pullRequestNumber = pullRequestNumber;
				}
			}
			else if (this.pullRequestNumber == null && pullRequestNumber != null) {
				this.pullRequestNumber = pullRequestNumber;
			}
		}

		private FileCandidate toFileCandidate() {
			return new FileCandidate(
					path,
					commitSha,
					pullRequestNumber,
					List.copyOf(sourceEvidenceIds),
					originSourceId,
					score
			);
		}
	}

	private record DecodedFile(String text) {
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
}

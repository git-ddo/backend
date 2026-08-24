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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

@Service
public class P1EvidenceCollector {

	static final int MAX_COMMIT_SUMMARIES = 20;
	static final int MAX_COMMIT_LIST = 100;
	static final int MAX_COMMIT_DETAIL_LOOKUPS = 40;
	static final int MAX_COMMIT_FILE_EVIDENCE = 8;
	static final int MAX_FILES_PER_COMMIT = 30;
	static final int MAX_PULL_REQUESTS = 10;
	static final int MAX_PULL_REQUEST_FETCH = 30;
	static final int MAX_FILES_PER_PR = 40;
	static final int MAX_COMMIT_MESSAGE_CHARS = 200;
	static final int MAX_PR_TITLE_CHARS = 120;

	private final GithubAnalysisClient githubAnalysisClient;
	private final ActivityImpactPolicy activityImpactPolicy;

	public P1EvidenceCollector(
			GithubAnalysisClient githubAnalysisClient,
			ActivityImpactPolicy activityImpactPolicy
	) {
		this.githubAnalysisClient = githubAnalysisClient;
		this.activityImpactPolicy = activityImpactPolicy;
	}

	public P0EvidenceSnapshot collect(
			String accessToken,
			EvaluationInputSnapshot inputSnapshot,
			P0EvidenceSnapshot p0Snapshot
	) {
		String githubLogin = inputSnapshot.githubLogin();
		List<P0EvidenceSnapshot.Evidence> evidence = new ArrayList<>(p0Snapshot.evidence());
		List<P0EvidenceSnapshot.Warning> warnings = new ArrayList<>(p0Snapshot.warnings());
		if (githubLogin == null || githubLogin.isBlank()) {
			warnings.add(warning(
					"MISSING_GITHUB_LOGIN",
					null,
					null,
					"GitHub 로그인 정보가 없어 활동 근거를 수집하지 않았습니다."
			));
			return new P0EvidenceSnapshot(
					p0Snapshot.schemaVersion(),
					p0Snapshot.extractorVersion(),
					p0Snapshot.collectedAt(),
					p0Snapshot.repositories(),
					evidence,
					warnings
			);
		}

		int nextId = evidence.size() + 1;
		for (P0EvidenceSnapshot.RepositorySnapshot repository : p0Snapshot.repositories()) {
			nextId = collectRepository(
					accessToken,
					githubLogin,
					repository,
					evidence,
					warnings,
					nextId
			);
		}
		return new P0EvidenceSnapshot(
				p0Snapshot.schemaVersion(),
				P0EvidenceSnapshot.P1_EXTRACTOR_VERSION,
				p0Snapshot.collectedAt(),
				p0Snapshot.repositories(),
				evidence,
				warnings
		);
	}

	private int collectRepository(
			String accessToken,
			String githubLogin,
			P0EvidenceSnapshot.RepositorySnapshot repository,
			List<P0EvidenceSnapshot.Evidence> evidence,
			List<P0EvidenceSnapshot.Warning> warnings,
			int nextId
	) {
		RepositoryName repositoryName = RepositoryName.parse(repository.fullName());
		String repositoryId = repository.repositoryId();
		String snapshotSha = repository.snapshotSha();
		List<String> sourceIds = new ArrayList<>();
		List<Instant> activityTimes = new ArrayList<>();
		int changedFileCount = 0;

		List<ScoredPullRequest> pullRequests = scorePullRequests(
				accessToken,
				githubLogin,
				repositoryName,
				repositoryId,
				warnings
		);
		List<ScoredPullRequest> selectedPullRequests = selectByImpact(
				pullRequests,
				MAX_PULL_REQUESTS,
				ScoredPullRequest::score
		);
		if (pullRequests.size() > selectedPullRequests.size()) {
			warnings.add(warning(
					"PULL_REQUEST_LIMIT_EXCEEDED",
					repositoryId,
					null,
					"Pull Request는 소스 변경 임팩트 기준으로 최대 "
							+ MAX_PULL_REQUESTS + "개까지만 Evidence에 포함했습니다."
			));
		}
		for (ScoredPullRequest scored : selectedPullRequests) {
			String evidenceId = evidenceId(nextId++);
			sourceIds.add(evidenceId);
			GithubPullRequestPayload pullRequest = scored.pullRequest();
			if (pullRequest.createdAt() != null) {
				activityTimes.add(pullRequest.createdAt());
			}
			changedFileCount += scored.files().size();
			evidence.add(activityEvidence(
					evidenceId,
					EvidenceKind.PULL_REQUEST,
					repositoryId,
					snapshotSha,
					null,
					pullRequest.head() == null ? null : pullRequest.head().sha(),
					pullRequest.number(),
					pullRequestContent(pullRequest, scored.files(), scored.truncatedFiles()),
					scored.truncatedFiles(),
					List.of()
			));
		}

		List<GithubCommitListItemPayload> listedCommits = githubAnalysisClient.fetchCommits(
				accessToken,
				repositoryName.owner(),
				repositoryName.name(),
				githubLogin,
				snapshotSha,
				MAX_COMMIT_LIST
		);
		if (listedCommits.size() >= MAX_COMMIT_LIST) {
			warnings.add(warning(
					"COMMIT_WINDOW_TRUNCATED",
					repositoryId,
					null,
					"커밋 목록은 최근 " + MAX_COMMIT_LIST + "개 창에서 임팩트를 계산했습니다."
			));
		}
		List<GithubCommitListItemPayload> regularCommits = listedCommits.stream()
				.filter(commit -> !commit.isMerge())
				.toList();
		int mergeCount = listedCommits.size() - regularCommits.size();
		if (mergeCount > 0) {
			warnings.add(warning(
					"MERGE_COMMITS_EXCLUDED",
					repositoryId,
					null,
					"merge 커밋 " + mergeCount + "개는 선정과 상세 조회에서 제외했습니다."
			));
		}
		List<ScoredCommit> rankedCommits = scoreCommits(
				accessToken,
				repositoryName,
				repositoryId,
				regularCommits,
				warnings
		);
		List<ScoredCommit> selectedCommits = selectByImpact(
				rankedCommits,
				MAX_COMMIT_SUMMARIES,
				ScoredCommit::score
		);
		if (rankedCommits.size() > selectedCommits.size()) {
			warnings.add(warning(
					"COMMIT_LIMIT_EXCEEDED",
					repositoryId,
					null,
					"커밋은 소스 변경 임팩트 기준으로 최대 "
							+ MAX_COMMIT_SUMMARIES + "개까지만 Evidence에 포함했습니다."
			));
		}

		int fileEvidence = 0;
		for (ScoredCommit scored : selectedCommits) {
			GithubCommitListItemPayload commit = scored.commit();
			String commitSha = commit.sha();
			String commitEvidenceId = evidenceId(nextId++);
			sourceIds.add(commitEvidenceId);
			Instant authoredAt = commit.commit() == null || commit.commit().author() == null
					? null
					: commit.commit().author().date();
			if (authoredAt != null) {
				activityTimes.add(authoredAt);
			}
			evidence.add(activityEvidence(
					commitEvidenceId,
					EvidenceKind.COMMIT_SUMMARY,
					repositoryId,
					snapshotSha,
					null,
					commitSha,
					null,
					commitContent(commit, scored.score()),
					false,
					List.of()
			));
			if (scored.detail() == null || fileEvidence >= MAX_COMMIT_FILE_EVIDENCE) {
				continue;
			}
			fileEvidence++;
			List<GithubCommitDetailPayload.FilePayload> files = scored.files();
			changedFileCount += files.size();
			evidence.add(activityEvidence(
					evidenceId(nextId++),
					EvidenceKind.CHANGED_FILES,
					repositoryId,
					snapshotSha,
					null,
					commitSha,
					null,
					changedFilesContent(scored.detail(), files, scored.truncatedFiles()),
					scored.truncatedFiles(),
					List.of(commitEvidenceId)
			));
		}

		if (sourceIds.isEmpty()) {
			warnings.add(warning(
					"NO_AUTHOR_ACTIVITY",
					repositoryId,
					null,
					githubLogin + " 사용자의 커밋·PR 근거를 찾지 못했습니다."
			));
		}
		evidence.add(activityEvidence(
				evidenceId(nextId++),
				EvidenceKind.ACTIVITY_SUMMARY,
				repositoryId,
				snapshotSha,
				null,
				snapshotSha,
				null,
				activitySummary(
						githubLogin,
						listedCommits.size(),
						selectedCommits.size(),
						selectedPullRequests.size(),
						changedFileCount,
						activityTimes
				),
				false,
				sourceIds
		));
		return nextId;
	}

	private List<ScoredPullRequest> scorePullRequests(
			String accessToken,
			String githubLogin,
			RepositoryName repositoryName,
			String repositoryId,
			List<P0EvidenceSnapshot.Warning> warnings
	) {
		List<GithubPullRequestPayload> authored = githubAnalysisClient.fetchPullRequests(
				accessToken,
				repositoryName.owner(),
				repositoryName.name(),
				MAX_PULL_REQUEST_FETCH
		).stream()
				.filter(pullRequest -> pullRequest.authoredBy(githubLogin))
				.toList();
		List<ScoredPullRequest> scored = new ArrayList<>();
		for (GithubPullRequestPayload pullRequest : authored) {
			List<GithubPullRequestFilePayload> files = githubAnalysisClient.fetchPullRequestFiles(
					accessToken,
					repositoryName.owner(),
					repositoryName.name(),
					pullRequest.number(),
					MAX_FILES_PER_PR + 1
			);
			boolean truncatedFiles = files.size() > MAX_FILES_PER_PR;
			if (truncatedFiles) {
				warnings.add(warning(
						"PR_FILE_LIMIT_EXCEEDED",
						repositoryId,
						null,
						"PR #" + pullRequest.number() + " 변경 파일은 최대 "
								+ MAX_FILES_PER_PR + "개까지만 포함했습니다."
				));
				files = files.stream().limit(MAX_FILES_PER_PR).toList();
			}
			double score = activityImpactPolicy.score(
					pullRequest.title(),
					files.stream()
							.map(file -> new ActivityImpactPolicy.FileChange(
									file.filename(),
									file.additions(),
									file.deletions()
							))
							.toList()
			);
			scored.add(new ScoredPullRequest(score, pullRequest, files, truncatedFiles));
		}
		scored.sort(Comparator.comparingDouble(ScoredPullRequest::score).reversed());
		return scored;
	}

	private List<ScoredCommit> scoreCommits(
			String accessToken,
			RepositoryName repositoryName,
			String repositoryId,
			List<GithubCommitListItemPayload> listedCommits,
			List<P0EvidenceSnapshot.Warning> warnings
	) {
		List<GithubCommitListItemPayload> inspect = inspectCandidates(listedCommits);
		if (listedCommits.size() > inspect.size()) {
			warnings.add(warning(
					"COMMIT_DETAIL_WINDOW",
					repositoryId,
					null,
					"임팩트 계산을 위해 최근·오래된 커밋 "
							+ inspect.size() + "개의 변경 파일을 조회했습니다."
			));
		}
		List<ScoredCommit> scored = new ArrayList<>();
		for (GithubCommitListItemPayload commit : inspect) {
			if (commit.isMerge()) {
				continue;
			}
			String commitSha = commit.sha();
			if (commitSha == null || commitSha.isBlank()) {
				continue;
			}
			GithubCommitDetailPayload detail = githubAnalysisClient.fetchCommitDetail(
					accessToken,
					repositoryName.owner(),
					repositoryName.name(),
					commitSha
			);
			List<GithubCommitDetailPayload.FilePayload> files = detail.files();
			boolean truncatedFiles = files.size() > MAX_FILES_PER_COMMIT;
			if (truncatedFiles) {
				warnings.add(warning(
						"COMMIT_FILE_LIMIT_EXCEEDED",
						repositoryId,
						null,
						"커밋 " + shortSha(commitSha) + " 변경 파일은 최대 "
								+ MAX_FILES_PER_COMMIT + "개까지만 포함했습니다."
				));
				files = files.stream().limit(MAX_FILES_PER_COMMIT).toList();
			}
			String message = commit.commit() == null ? "" : commit.commit().message();
			double score = activityImpactPolicy.score(
					message,
					files.stream()
							.map(file -> new ActivityImpactPolicy.FileChange(
									file.filename(),
									file.additions(),
									file.deletions()
							))
							.toList()
			);
			scored.add(new ScoredCommit(score, commit, detail, files, truncatedFiles));
		}
		scored.sort(Comparator.comparingDouble(ScoredCommit::score).reversed());
		return scored;
	}

	private <T> List<T> selectByImpact(List<T> ranked, int limit, ToDoubleFunction<T> score) {
		List<T> positive = ranked.stream()
				.filter(item -> score.applyAsDouble(item) > 0)
				.toList();
		List<T> pool = positive.isEmpty() ? ranked : positive;
		return pool.stream().limit(limit).toList();
	}

	private List<GithubCommitListItemPayload> inspectCandidates(
			List<GithubCommitListItemPayload> commits
	) {
		if (commits.size() <= MAX_COMMIT_DETAIL_LOOKUPS) {
			return commits;
		}
		int edge = MAX_COMMIT_DETAIL_LOOKUPS / 2;
		LinkedHashMap<String, GithubCommitListItemPayload> selected = new LinkedHashMap<>();
		commits.stream().limit(edge).forEach(commit -> selected.putIfAbsent(commit.sha(), commit));
		commits.stream()
				.skip(Math.max(0, commits.size() - edge))
				.forEach(commit -> selected.putIfAbsent(commit.sha(), commit));
		return List.copyOf(selected.values());
	}

	private P0EvidenceSnapshot.Evidence activityEvidence(
			String evidenceId,
			EvidenceKind kind,
			String repositoryId,
			String snapshotSha,
			String path,
			String commitSha,
			Integer pullRequestNumber,
			String content,
			boolean truncated,
			List<String> sourceEvidenceRefs
	) {
		boolean derived = kind == EvidenceKind.ACTIVITY_SUMMARY;
		return new P0EvidenceSnapshot.Evidence(
				evidenceId,
				derived ? EvidenceType.BACKEND_DERIVED : EvidenceType.GITHUB_ACTIVITY,
				kind,
				AnalysisDepth.P1,
				repositoryId,
				snapshotSha,
				path,
				commitSha,
				pullRequestNumber,
				null,
				null,
				content,
				sha256(content),
				truncated,
				sourceEvidenceRefs
		);
	}

	private String pullRequestContent(
			GithubPullRequestPayload pullRequest,
			List<GithubPullRequestFilePayload> files,
			boolean truncatedFiles
	) {
		String title = truncate(pullRequest.title(), MAX_PR_TITLE_CHARS);
		String fileList = files.stream()
				.map(file -> file.status() + "\t" + file.filename()
						+ "\t+" + file.additions() + "/-" + file.deletions())
				.reduce((left, right) -> left + "\n" + right)
				.orElse("");
		return """
				number=%d
				title=%s
				state=%s
				merged=%s
				createdAt=%s
				mergedAt=%s
				headSha=%s
				truncatedFiles=%s
				files:
				%s
				""".formatted(
				pullRequest.number(),
				nullToEmpty(title),
				nullToEmpty(pullRequest.state()),
				pullRequest.mergedAt() != null,
				nullToEmpty(pullRequest.createdAt()),
				nullToEmpty(pullRequest.mergedAt()),
				pullRequest.head() == null ? "" : nullToEmpty(pullRequest.head().sha()),
				truncatedFiles,
				fileList
		).strip();
	}

	private String commitContent(GithubCommitListItemPayload commit, double impactScore) {
		String message = commit.commit() == null ? "" : firstLine(commit.commit().message());
		Instant authoredAt = commit.commit() == null || commit.commit().author() == null
				? null
				: commit.commit().author().date();
		String authorLogin = commit.author() == null ? "" : nullToEmpty(commit.author().login());
		return """
				sha=%s
				author=%s
				authoredAt=%s
				impactScore=%s
				message=%s
				""".formatted(
				nullToEmpty(commit.sha()),
				authorLogin,
				nullToEmpty(authoredAt),
				"%.1f".formatted(impactScore),
				truncate(message, MAX_COMMIT_MESSAGE_CHARS)
		).strip();
	}

	private String changedFilesContent(
			GithubCommitDetailPayload detail,
			List<GithubCommitDetailPayload.FilePayload> files,
			boolean truncatedFiles
	) {
		String fileList = files.stream()
				.map(file -> file.status() + "\t" + file.filename()
						+ "\t+" + file.additions() + "/-" + file.deletions())
				.reduce((left, right) -> left + "\n" + right)
				.orElse("");
		int additions = detail.stats() == null ? 0 : detail.stats().additions();
		int deletions = detail.stats() == null ? 0 : detail.stats().deletions();
		return """
				sha=%s
				additions=%d
				deletions=%d
				truncatedFiles=%s
				files:
				%s
				""".formatted(
				nullToEmpty(detail.sha()),
				additions,
				deletions,
				truncatedFiles,
				fileList
		).strip();
	}

	private String activitySummary(
			String githubLogin,
			int listedCommitCount,
			int selectedCommitCount,
			int pullRequestCount,
			int changedFileCount,
			List<Instant> activityTimes
	) {
		Instant from = activityTimes.stream().filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
		Instant to = activityTimes.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
		return """
				author=%s
				listedCommitCount=%d
				selectedCommitCount=%d
				selection=SOURCE_IMPACT
				pullRequestCount=%d
				changedFileCount=%d
				authoredFrom=%s
				authoredTo=%s
				""".formatted(
				githubLogin,
				listedCommitCount,
				selectedCommitCount,
				pullRequestCount,
				changedFileCount,
				nullToEmpty(from),
				nullToEmpty(to)
		).strip();
	}

	private String evidenceId(int sequence) {
		return "ev_%03d".formatted(sequence);
	}

	private String firstLine(String message) {
		if (message == null || message.isBlank()) {
			return "";
		}
		int newline = message.indexOf('\n');
		return newline < 0 ? message.strip() : message.substring(0, newline).strip();
	}

	private String truncate(String value, int maxChars) {
		if (value == null) {
			return "";
		}
		return value.length() <= maxChars ? value : value.substring(0, maxChars);
	}

	private String shortSha(String sha) {
		return sha.length() <= 7 ? sha : sha.substring(0, 7);
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

	private String nullToEmpty(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private record ScoredPullRequest(
			double score,
			GithubPullRequestPayload pullRequest,
			List<GithubPullRequestFilePayload> files,
			boolean truncatedFiles
	) {
	}

	private record ScoredCommit(
			double score,
			GithubCommitListItemPayload commit,
			GithubCommitDetailPayload detail,
			List<GithubCommitDetailPayload.FilePayload> files,
			boolean truncatedFiles
	) {
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

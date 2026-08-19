package com.gitddo.github.client;

import java.util.List;

public record GithubCommitDetailPayload(
		String sha,
		StatsPayload stats,
		List<FilePayload> files
) {

	public GithubCommitDetailPayload {
		files = files == null ? List.of() : List.copyOf(files);
	}

	public record StatsPayload(
			int additions,
			int deletions,
			int total
	) {
	}

	public record FilePayload(
			String filename,
			String status,
			int additions,
			int deletions
	) {
	}
}

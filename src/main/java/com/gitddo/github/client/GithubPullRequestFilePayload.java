package com.gitddo.github.client;

public record GithubPullRequestFilePayload(
		String filename,
		String status,
		int additions,
		int deletions
) {
}

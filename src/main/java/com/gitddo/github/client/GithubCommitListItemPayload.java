package com.gitddo.github.client;

import java.time.Instant;

public record GithubCommitListItemPayload(
		String sha,
		CommitPayload commit,
		UserPayload author
) {

	public record CommitPayload(
			String message,
			GitUserPayload author
	) {
	}

	public record GitUserPayload(
			String name,
			Instant date
	) {
	}

	public record UserPayload(
			String login
	) {
	}
}

package com.gitddo.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GithubPullRequestPayload(
		int number,
		String title,
		String state,
		UserPayload user,
		HeadPayload head,
		@JsonProperty("created_at")
		Instant createdAt,
		@JsonProperty("merged_at")
		Instant mergedAt
) {

	public boolean authoredBy(String login) {
		return login != null
				&& user != null
				&& login.equalsIgnoreCase(user.login());
	}

	public record UserPayload(
			String login
	) {
	}

	public record HeadPayload(
			String sha
	) {
	}
}

package com.gitddo.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GithubRepositoryPayload(
		Long id,
		String name,
		@JsonProperty("full_name")
		String fullName,
		String description,
		@JsonProperty("html_url")
		String htmlUrl,
		String language,
		boolean fork,
		boolean archived,
		@JsonProperty("default_branch")
		String defaultBranch,
		@JsonProperty("stargazers_count")
		int stargazersCount,
		@JsonProperty("forks_count")
		int forksCount,
		@JsonProperty("pushed_at")
		Instant pushedAt,
		@JsonProperty("updated_at")
		Instant updatedAt
) {
}

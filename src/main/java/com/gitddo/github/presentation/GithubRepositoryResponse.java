package com.gitddo.github.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "GitHub public 저장소")
public record GithubRepositoryResponse(
		@Schema(example = "123456789")
		Long id,
		@Schema(example = "gitddo")
		String name,
		@Schema(example = "jhkim2da/gitddo")
		String fullName,
		String description,
		String htmlUrl,
		String language,
		boolean fork,
		boolean archived,
		String defaultBranch,
		int stargazersCount,
		int forksCount,
		Instant pushedAt,
		Instant updatedAt
) {
}

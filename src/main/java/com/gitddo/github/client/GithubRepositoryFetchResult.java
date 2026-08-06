package com.gitddo.github.client;

import java.util.List;

public record GithubRepositoryFetchResult(
		List<GithubRepositoryPayload> repositories,
		Integer rateLimitRemaining
) {
}

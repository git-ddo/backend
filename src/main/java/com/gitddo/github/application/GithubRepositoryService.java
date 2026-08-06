package com.gitddo.github.application;

import com.gitddo.github.client.GithubRepositoryClient;
import com.gitddo.github.client.GithubRepositoryFetchResult;
import com.gitddo.github.client.GithubRepositoryPayload;
import com.gitddo.github.presentation.GithubRepositoriesResponse;
import com.gitddo.github.presentation.GithubRepositoryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GithubRepositoryService {

	private final GithubRepositoryClient githubRepositoryClient;

	public GithubRepositoryService(GithubRepositoryClient githubRepositoryClient) {
		this.githubRepositoryClient = githubRepositoryClient;
	}

	public GithubRepositoriesResponse getOwnedPublicRepositories(String accessToken) {
		GithubRepositoryFetchResult result =
				githubRepositoryClient.fetchOwnedPublicRepositories(accessToken);

		List<GithubRepositoryResponse> repositories = result.repositories().stream()
				.map(this::toResponse)
				.toList();

		return new GithubRepositoriesResponse(
				repositories.size(),
				result.rateLimitRemaining(),
				repositories
		);
	}

	private GithubRepositoryResponse toResponse(GithubRepositoryPayload repository) {
		return new GithubRepositoryResponse(
				repository.id(),
				repository.name(),
				repository.fullName(),
				repository.description(),
				repository.htmlUrl(),
				repository.language(),
				repository.fork(),
				repository.archived(),
				repository.defaultBranch(),
				repository.stargazersCount(),
				repository.forksCount(),
				repository.pushedAt(),
				repository.updatedAt()
		);
	}
}

package com.gitddo.github.application;

import com.gitddo.github.client.GithubRepositoryClient;
import com.gitddo.github.client.GithubRepositoryFetchResult;
import com.gitddo.github.client.GithubRepositoryPayload;
import com.gitddo.github.domain.GithubRepository;
import com.gitddo.github.domain.GithubRepositoryRepository;
import com.gitddo.github.domain.UserRepositoryAccess;
import com.gitddo.github.domain.UserRepositoryAccessRepository;
import com.gitddo.github.presentation.GithubRepositoriesResponse;
import com.gitddo.github.presentation.GithubRepositoryResponse;
import com.gitddo.github.presentation.GithubRepositorySearchResponse;
import com.gitddo.member.domain.GithubUser;
import com.gitddo.member.domain.GithubUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GithubRepositoryService {

	private final GithubRepositoryClient githubRepositoryClient;
	private final GithubRepositoryRepository githubRepositoryRepository;
	private final UserRepositoryAccessRepository userRepositoryAccessRepository;
	private final GithubUserRepository githubUserRepository;

	public GithubRepositoryService(
			GithubRepositoryClient githubRepositoryClient,
			GithubRepositoryRepository githubRepositoryRepository,
			UserRepositoryAccessRepository userRepositoryAccessRepository,
			GithubUserRepository githubUserRepository
	) {
		this.githubRepositoryClient = githubRepositoryClient;
		this.githubRepositoryRepository = githubRepositoryRepository;
		this.userRepositoryAccessRepository = userRepositoryAccessRepository;
		this.githubUserRepository = githubUserRepository;
	}

	@Transactional
	public GithubRepositoriesResponse getParticipatingPublicRepositories(String accessToken, Long githubUserId) {
		GithubRepositoryFetchResult result =
				githubRepositoryClient.fetchParticipatingPublicRepositories(accessToken);

		GithubUser githubUser = findGithubUser(githubUserId);
		synchronize(githubUser, result.repositories());

		List<GithubRepositoryResponse> repositories = result.repositories().stream()
				.map(this::toResponse)
				.toList();

		return new GithubRepositoriesResponse(
				repositories.size(),
				result.rateLimitRemaining(),
				repositories
		);
	}

	@Transactional(readOnly = true)
	public GithubRepositorySearchResponse search(Long githubUserId, String owner, String name) {
		List<GithubRepositoryResponse> repositories = githubRepositoryRepository
				.searchAccessibleRepositories(
						githubUserId,
						normalize(owner),
						normalize(name)
				)
				.stream()
				.map(this::toResponse)
				.toList();

		return new GithubRepositorySearchResponse(repositories.size(), repositories);
	}

	private void synchronize(GithubUser githubUser, List<GithubRepositoryPayload> repositories) {
		Set<Long> seenRepositoryIds = repositories.stream()
				.map(payload -> synchronizeRepository(githubUser, payload).getId())
				.collect(Collectors.toSet());

		userRepositoryAccessRepository.findByGithubUserIdAndActiveTrue(githubUser.getId()).stream()
				.filter(access -> !seenRepositoryIds.contains(access.getRepositoryId()))
				.forEach(UserRepositoryAccess::deactivate);
	}

	private GithubRepository synchronizeRepository(
			GithubUser githubUser,
			GithubRepositoryPayload payload
	) {
		GithubRepository repository = githubRepositoryRepository.findByGithubId(payload.id())
				.map(existing -> {
					existing.update(payload);
					return existing;
				})
				.orElseGet(() -> githubRepositoryRepository.save(new GithubRepository(payload)));

		userRepositoryAccessRepository.findByGithubUserIdAndGithubRepositoryId(
						githubUser.getId(),
						repository.getId()
				)
				.ifPresentOrElse(
						UserRepositoryAccess::markSeen,
						() -> userRepositoryAccessRepository.save(
								new UserRepositoryAccess(githubUser, repository)
						)
				);
		return repository;
	}

	private GithubUser findGithubUser(Long githubUserId) {
		return githubUserRepository.findByGithubId(githubUserId)
				.orElseThrow(() -> new IllegalStateException("로그인 사용자 정보가 DB에 없습니다."));
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
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

	private GithubRepositoryResponse toResponse(GithubRepository repository) {
		return new GithubRepositoryResponse(
				repository.getGithubId(),
				repository.getName(),
				repository.getFullName(),
				repository.getDescription(),
				repository.getHtmlUrl(),
				repository.getLanguage(),
				repository.isFork(),
				repository.isArchived(),
				repository.getDefaultBranch(),
				repository.getStargazersCount(),
				repository.getForksCount(),
				repository.getPushedAt(),
				repository.getGithubUpdatedAt()
		);
	}
}

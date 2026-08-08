package com.gitddo.github.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryAccessRepository extends JpaRepository<UserRepositoryAccess, Long> {

	Optional<UserRepositoryAccess> findByGithubUserIdAndGithubRepositoryId(
			Long githubUserId,
			Long repositoryId
	);

	List<UserRepositoryAccess> findByGithubUserIdAndActiveTrue(Long githubUserId);

	boolean existsByGithubUserGithubIdAndGithubRepositoryGithubIdAndActiveTrue(
			Long githubUserId,
			Long repositoryGithubId
	);
}

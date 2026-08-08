package com.gitddo.github.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

	Optional<GithubRepository> findByGithubId(Long githubId);

	@Query("""
			SELECT repository
			FROM GithubRepository repository
			JOIN UserRepositoryAccess access ON access.githubRepository = repository
			WHERE access.githubUser.githubId = :githubUserId
			  AND access.active = true
			  AND LOWER(repository.ownerLogin) LIKE LOWER(CONCAT('%', :owner, '%'))
			  AND LOWER(repository.name) LIKE LOWER(CONCAT('%', :name, '%'))
			ORDER BY repository.githubUpdatedAt DESC
			""")
	List<GithubRepository> searchAccessibleRepositories(
			@Param("githubUserId") Long githubUserId,
			@Param("owner") String owner,
			@Param("name") String name
	);
}

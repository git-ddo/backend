package com.gitddo.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubUserRepository extends JpaRepository<GithubUser, Long> {

	Optional<GithubUser> findByGithubId(Long githubId);
}

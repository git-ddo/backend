package com.gitddo.member.application;

import com.gitddo.member.domain.GithubUser;
import com.gitddo.member.domain.GithubUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GithubUserService {

	private final GithubUserRepository githubUserRepository;

	public GithubUserService(GithubUserRepository githubUserRepository) {
		this.githubUserRepository = githubUserRepository;
	}

	@Transactional
	public GithubUser synchronize(Long githubId, String login, String avatarUrl) {
		return githubUserRepository.findByGithubId(githubId)
				.map(user -> {
					user.updateProfile(login, avatarUrl);
					return user;
				})
				.orElseGet(() -> githubUserRepository.save(new GithubUser(githubId, login, avatarUrl)));
	}
}

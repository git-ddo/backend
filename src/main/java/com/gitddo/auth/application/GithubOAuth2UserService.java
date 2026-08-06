package com.gitddo.auth.application;

import com.gitddo.member.application.GithubUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
	private final GithubUserService githubUserService;

	public GithubOAuth2UserService(GithubUserService githubUserService) {
		this.githubUserService = githubUserService;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oauthUser = delegate.loadUser(userRequest);

		Number githubId = oauthUser.getAttribute("id");
		String login = oauthUser.getAttribute("login");
		String avatarUrl = oauthUser.getAttribute("avatar_url");

		if (githubId == null || login == null || login.isBlank()) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("invalid_github_profile"),
					"GitHub 사용자 응답에 필수 정보가 없습니다."
			);
		}

		githubUserService.synchronize(githubId.longValue(), login, avatarUrl);
		return oauthUser;
	}
}

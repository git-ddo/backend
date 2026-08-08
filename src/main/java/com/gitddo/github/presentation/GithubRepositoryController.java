package com.gitddo.github.presentation;

import com.gitddo.github.application.GithubRepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/github/repositories")
@Tag(name = "GitHub", description = "GitHub 데이터 연동 API")
public class GithubRepositoryController {

	private final GithubRepositoryService githubRepositoryService;

	public GithubRepositoryController(GithubRepositoryService githubRepositoryService) {
		this.githubRepositoryService = githubRepositoryService;
	}

	@GetMapping
	@Operation(
			summary = "참여한 public 저장소 전체 조회",
			description = "로그인 사용자가 소유하거나 조직 구성원 및 협업자로 참여한 public 저장소를 모두 조회하고 동기화합니다.",
			security = @SecurityRequirement(name = "sessionAuth")
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "저장소 조회 성공"),
			@ApiResponse(responseCode = "401", description = "GitHub 로그인이 필요함"),
			@ApiResponse(responseCode = "502", description = "GitHub API 호출 실패")
	})
	GithubRepositoriesResponse getRepositories(
			@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OAuth2User oauthUser
	) {
		return githubRepositoryService.getParticipatingPublicRepositories(
				authorizedClient.getAccessToken().getTokenValue(),
				githubUserId(oauthUser)
		);
	}

	@GetMapping("/search")
	@Operation(
			summary = "동기화된 저장소 검색",
			description = "동기화된 참여 저장소를 개인·조직 소유자와 프로젝트 이름으로 대소문자 구분 없이 부분 일치 검색합니다.",
			security = @SecurityRequirement(name = "sessionAuth")
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "저장소 검색 성공"),
			@ApiResponse(responseCode = "401", description = "GitHub 로그인이 필요함")
	})
	GithubRepositorySearchResponse searchRepositories(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@RequestParam(required = false) String owner,
			@RequestParam(required = false) String name
	) {
		return githubRepositoryService.search(githubUserId(oauthUser), owner, name);
	}

	private Long githubUserId(OAuth2User oauthUser) {
		Number githubId = oauthUser.getAttribute("id");
		if (githubId == null) {
			throw new IllegalStateException("GitHub 사용자 ID가 없습니다.");
		}
		return githubId.longValue();
	}
}

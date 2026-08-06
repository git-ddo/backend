package com.gitddo.github.presentation;

import com.gitddo.github.application.GithubRepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
			summary = "내 public 저장소 전체 조회",
			description = "로그인 사용자가 소유한 public 저장소를 GitHub에서 모두 조회합니다.",
			security = @SecurityRequirement(name = "sessionAuth")
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "저장소 조회 성공"),
			@ApiResponse(responseCode = "401", description = "GitHub 로그인이 필요함"),
			@ApiResponse(responseCode = "502", description = "GitHub API 호출 실패")
	})
	GithubRepositoriesResponse getRepositories(
			@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient
	) {
		return githubRepositoryService.getOwnedPublicRepositories(
				authorizedClient.getAccessToken().getTokenValue()
		);
	}
}

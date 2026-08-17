package com.gitddo.portfolio.presentation;

import com.gitddo.portfolio.application.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolio", description = "코칭 포트폴리오 API")
@SecurityRequirement(name = "sessionAuth")
public class PortfolioController {

	private final PortfolioService portfolioService;

	public PortfolioController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "코칭 포트폴리오 생성")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "포트폴리오 생성 성공"),
			@ApiResponse(responseCode = "400", description = "입력값 또는 저장소 선택 오류"),
			@ApiResponse(responseCode = "401", description = "GitHub 로그인이 필요함")
	})
	PortfolioResponse create(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@Valid @RequestBody CreatePortfolioRequest request
	) {
		return portfolioService.create(githubUserId(oauthUser), request);
	}

	@GetMapping
	@Operation(summary = "내 코칭 포트폴리오 목록 조회")
	List<PortfolioResponse> getAll(@AuthenticationPrincipal OAuth2User oauthUser) {
		return portfolioService.getAll(githubUserId(oauthUser));
	}

	@GetMapping("/{portfolioId}")
	@Operation(summary = "내 코칭 포트폴리오 상세 조회")
	PortfolioResponse get(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@PathVariable Long portfolioId
	) {
		return portfolioService.get(githubUserId(oauthUser), portfolioId);
	}

	@PutMapping("/{portfolioId}")
	@Operation(summary = "내 코칭 포트폴리오 수정")
	PortfolioResponse update(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@PathVariable Long portfolioId,
			@Valid @RequestBody UpdatePortfolioRequest request
	) {
		return portfolioService.update(githubUserId(oauthUser), portfolioId, request);
	}

	@DeleteMapping("/{portfolioId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "내 코칭 포트폴리오 삭제")
	void delete(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@PathVariable Long portfolioId
	) {
		portfolioService.delete(githubUserId(oauthUser), portfolioId);
	}

	@PostMapping("/{portfolioId}/repositories")
	@Operation(
			summary = "포트폴리오에 저장소 추가",
			description = "저장소 조회 또는 검색 결과의 GitHub 저장소 ID를 선택해 포트폴리오에 추가합니다."
	)
	PortfolioResponse addRepository(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@PathVariable Long portfolioId,
			@Valid @RequestBody AddPortfolioRepositoryRequest request
	) {
		return portfolioService.addRepository(
				githubUserId(oauthUser),
				portfolioId,
				request
		);
	}

	@PutMapping("/{portfolioId}/repositories/{repositoryId}")
	@Operation(summary = "포트폴리오 저장소 역할 및 기여 내용 수정")
	PortfolioResponse updateRepository(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@PathVariable Long portfolioId,
			@PathVariable Long repositoryId,
			@Valid @RequestBody UpdatePortfolioRepositoryRequest request
	) {
		return portfolioService.updateRepository(
				githubUserId(oauthUser),
				portfolioId,
				repositoryId,
				request
		);
	}

	@DeleteMapping("/{portfolioId}/repositories/{repositoryId}")
	@Operation(summary = "포트폴리오에서 저장소 삭제")
	PortfolioResponse removeRepository(
			@AuthenticationPrincipal OAuth2User oauthUser,
			@PathVariable Long portfolioId,
			@PathVariable Long repositoryId,
			@RequestParam Long version
	) {
		return portfolioService.removeRepository(
				githubUserId(oauthUser),
				portfolioId,
				repositoryId,
				version
		);
	}

	private Long githubUserId(OAuth2User oauthUser) {
		Number githubId = oauthUser.getAttribute("id");
		if (githubId == null) {
			throw new IllegalStateException("GitHub 사용자 ID가 없습니다.");
		}
		return githubId.longValue();
	}
}

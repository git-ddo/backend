package com.gitddo.analysis.presentation;

import com.gitddo.analysis.application.EvaluationOrchestrationService;
import com.gitddo.analysis.application.EvaluationService;
import com.gitddo.analysis.domain.EvaluationRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/evaluations")
@Tag(name = "Evaluation", description = "포트폴리오 P0 Evidence 수집 및 평가 API")
@SecurityRequirement(name = "sessionAuth")
public class EvaluationController {

	private final EvaluationOrchestrationService orchestrationService;
	private final EvaluationService evaluationService;

	public EvaluationController(
			EvaluationOrchestrationService orchestrationService,
			EvaluationService evaluationService
	) {
		this.orchestrationService = orchestrationService;
		this.evaluationService = evaluationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(
			summary = "포트폴리오 P0 평가 요청",
			description = "비동기로 GitHub Snapshot SHA를 고정하고 P0 Evidence를 수집합니다."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "202", description = "평가 요청 접수"),
			@ApiResponse(responseCode = "400", description = "저장소가 없는 포트폴리오"),
			@ApiResponse(responseCode = "401", description = "GitHub 로그인이 필요함"),
			@ApiResponse(responseCode = "404", description = "포트폴리오를 찾을 수 없음")
	})
	EvaluationResponse request(
			@PathVariable Long portfolioId,
			@RegisteredOAuth2AuthorizedClient("github")
			OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OAuth2User oauthUser
	) {
		EvaluationRun run = orchestrationService.request(
				githubUserId(oauthUser),
				portfolioId,
				authorizedClient.getAccessToken().getTokenValue()
		);
		return EvaluationResponse.from(run);
	}

	@GetMapping("/{analysisId}")
	@Operation(
			summary = "P0 평가 상태 및 Evidence 조회",
			description = "EVIDENCE_READY 상태가 되면 수집된 P0 Evidence와 경고를 반환합니다."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "평가 상태 조회 성공"),
			@ApiResponse(responseCode = "401", description = "GitHub 로그인이 필요함"),
			@ApiResponse(responseCode = "404", description = "평가를 찾을 수 없음")
	})
	EvaluationResponse get(
			@PathVariable Long portfolioId,
			@PathVariable UUID analysisId,
			@AuthenticationPrincipal OAuth2User oauthUser
	) {
		EvaluationRun run = evaluationService.get(
				githubUserId(oauthUser),
				portfolioId,
				analysisId
		);
		return EvaluationResponse.from(run);
	}

	private Long githubUserId(OAuth2User oauthUser) {
		Number githubId = oauthUser.getAttribute("id");
		if (githubId == null) {
			throw new IllegalStateException("GitHub 사용자 ID가 없습니다.");
		}
		return githubId.longValue();
	}
}

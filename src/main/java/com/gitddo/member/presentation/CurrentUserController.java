package com.gitddo.member.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Member", description = "로그인 사용자 API")
public class CurrentUserController {

	@GetMapping
	@Operation(
			summary = "현재 로그인 사용자 조회",
			description = "GitHub OAuth로 로그인한 사용자의 프로필을 반환합니다.",
			security = @SecurityRequirement(name = "sessionAuth")
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "사용자 조회 성공"),
			@ApiResponse(responseCode = "401", description = "로그인이 필요함")
	})
	CurrentUserResponse getCurrentUser(@AuthenticationPrincipal OAuth2User oauthUser) {
		Number githubId = oauthUser.getAttribute("id");

		return new CurrentUserResponse(
				githubId == null ? null : githubId.longValue(),
				oauthUser.getAttribute("login"),
				oauthUser.getAttribute("avatar_url")
		);
	}
}

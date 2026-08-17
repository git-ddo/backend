package com.gitddo.auth.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/csrf")
@Tag(name = "Authentication", description = "세션 인증 보안 API")
public class CsrfTokenController {

	private final CookieCsrfTokenRepository csrfTokenRepository;

	public CsrfTokenController(CookieCsrfTokenRepository csrfTokenRepository) {
		this.csrfTokenRepository = csrfTokenRepository;
	}

	@GetMapping
	@Operation(
			summary = "CSRF 토큰 발급",
			description = "POST, PUT, DELETE 요청의 X-XSRF-TOKEN 헤더에 넣을 토큰을 반환합니다.",
			security = @SecurityRequirement(name = "sessionAuth")
	)
	CsrfTokenResponse getToken(
			HttpServletRequest request,
			HttpServletResponse response
	) {
		CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
		csrfTokenRepository.saveToken(csrfToken, request, response);
		return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
	}
}

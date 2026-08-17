package com.gitddo.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상태 변경 API에 사용할 CSRF 토큰")
public record CsrfTokenResponse(
		String token,
		String headerName
) {
}

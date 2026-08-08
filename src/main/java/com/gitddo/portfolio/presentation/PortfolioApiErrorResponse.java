package com.gitddo.portfolio.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "포트폴리오 API 오류")
public record PortfolioApiErrorResponse(
		String code,
		String message
) {
}

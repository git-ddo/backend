package com.gitddo.portfolio.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddPortfolioRepositoryRequest(
		@NotNull
		@PositiveOrZero
		@Schema(description = "마지막으로 조회한 포트폴리오 버전", example = "0")
		Long version,
		@NotNull
		@Schema(description = "검색 또는 조회에서 선택한 GitHub 저장소 ID")
		Long repositoryId,
		@NotBlank
		@Size(max = 10000)
		String contributionDescription,
		@Size(max = 2000)
		String roleSummary,
		@NotEmpty
		List<@Valid PortfolioRoleRequest> roles
) {
}

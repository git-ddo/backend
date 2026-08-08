package com.gitddo.portfolio.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortfolioRepositoryRequest(
		@NotNull
		@Schema(description = "GitHub 저장소 ID", example = "123456789")
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

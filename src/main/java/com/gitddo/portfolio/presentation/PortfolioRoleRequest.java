package com.gitddo.portfolio.presentation;

import com.gitddo.portfolio.domain.ParticipationLevel;
import com.gitddo.portfolio.domain.RepositoryRoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PortfolioRoleRequest(
		@NotNull
		@Schema(example = "BACKEND")
		RepositoryRoleType roleType,
		@NotNull
		@Schema(example = "LEAD")
		ParticipationLevel participationLevel,
		@Schema(description = "저장소의 대표 역할 여부", example = "true")
		boolean primary
) {
}

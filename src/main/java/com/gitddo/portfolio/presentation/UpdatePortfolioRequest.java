package com.gitddo.portfolio.presentation;

import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.TargetLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record UpdatePortfolioRequest(
		@NotNull
		@PositiveOrZero
		@Schema(description = "마지막으로 조회한 포트폴리오 버전", example = "0")
		Long version,
		@NotBlank
		@Size(max = 120)
		String title,
		@NotNull
		EvaluationPurpose evaluationPurpose,
		TargetLevel targetLevel,
		@NotEmpty
		Set<EvaluationArea> evaluationAreas,
		@NotNull
		@Size(min = 1, max = 5)
		List<@Valid PortfolioRepositoryRequest> repositories
) {
}

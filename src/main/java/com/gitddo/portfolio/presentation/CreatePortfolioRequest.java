package com.gitddo.portfolio.presentation;

import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.TargetLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record CreatePortfolioRequest(
		@NotBlank
		@Size(max = 120)
		@Schema(example = "백엔드 취업 코칭 포트폴리오")
		String title,
		@NotNull
		@Schema(example = "TECH_INTERVIEW")
		EvaluationPurpose evaluationPurpose,
		@Schema(example = "JUNIOR")
		TargetLevel targetLevel,
		@NotEmpty
		Set<EvaluationArea> evaluationAreas,
		@NotNull
		@Size(max = 5)
		List<@Valid PortfolioRepositoryRequest> repositories
) {
}

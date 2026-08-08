package com.gitddo.analysis.domain;

import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.ParticipationLevel;
import com.gitddo.portfolio.domain.RepositoryRoleType;
import com.gitddo.portfolio.domain.TargetLevel;

import java.util.List;
import java.util.Set;

public record EvaluationInputSnapshot(
		int schemaVersion,
		Long portfolioId,
		long portfolioVersion,
		String title,
		EvaluationPurpose evaluationPurpose,
		TargetLevel targetLevel,
		Set<EvaluationArea> evaluationAreas,
		List<RepositorySnapshot> repositories
) {

	public static final int CURRENT_SCHEMA_VERSION = 1;

	public record RepositorySnapshot(
			Long githubRepositoryId,
			String fullName,
			String htmlUrl,
			String language,
			String contributionDescription,
			String roleSummary,
			List<RoleSnapshot> roles
	) {
	}

	public record RoleSnapshot(
			RepositoryRoleType roleType,
			ParticipationLevel participationLevel,
			boolean primary
	) {
	}
}

package com.gitddo.portfolio.presentation;

import com.gitddo.github.domain.GithubRepository;
import com.gitddo.portfolio.domain.EvaluationArea;
import com.gitddo.portfolio.domain.EvaluationPurpose;
import com.gitddo.portfolio.domain.ParticipationLevel;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepositoryEntry;
import com.gitddo.portfolio.domain.RepositoryRoleAssignment;
import com.gitddo.portfolio.domain.RepositoryRoleType;
import com.gitddo.portfolio.domain.TargetLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Schema(description = "코칭 포트폴리오")
public record PortfolioResponse(
		Long id,
		String title,
		EvaluationPurpose evaluationPurpose,
		TargetLevel targetLevel,
		Set<EvaluationArea> evaluationAreas,
		long version,
		List<RepositoryEntryResponse> repositories,
		Instant createdAt,
		Instant updatedAt
) {

	public static PortfolioResponse from(Portfolio portfolio) {
		return new PortfolioResponse(
				portfolio.getId(),
				portfolio.getTitle(),
				portfolio.getEvaluationPurpose(),
				portfolio.getTargetLevel(),
				portfolio.getEvaluationAreas(),
				portfolio.getVersion(),
				portfolio.getRepositories().stream()
						.map(RepositoryEntryResponse::from)
						.toList(),
				portfolio.getCreatedAt(),
				portfolio.getUpdatedAt()
		);
	}

	public record RepositoryEntryResponse(
			Long repositoryId,
			String fullName,
			String htmlUrl,
			String language,
			String contributionDescription,
			String roleSummary,
			int displayOrder,
			List<RoleResponse> roles
	) {

		private static RepositoryEntryResponse from(PortfolioRepositoryEntry entry) {
			GithubRepository repository = entry.getGithubRepository();
			return new RepositoryEntryResponse(
					repository.getGithubId(),
					repository.getFullName(),
					repository.getHtmlUrl(),
					repository.getLanguage(),
					entry.getContributionDescription(),
					entry.getRoleSummary(),
					entry.getDisplayOrder(),
					entry.getRoles().stream().map(RoleResponse::from).toList()
			);
		}
	}

	public record RoleResponse(
			RepositoryRoleType roleType,
			ParticipationLevel participationLevel,
			boolean primary
	) {

		private static RoleResponse from(RepositoryRoleAssignment role) {
			return new RoleResponse(
					role.getRoleType(),
					role.getParticipationLevel(),
					role.isPrimary()
			);
		}
	}
}

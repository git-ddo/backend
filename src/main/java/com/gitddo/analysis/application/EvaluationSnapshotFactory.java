package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationInputSnapshot;
import com.gitddo.github.domain.GithubRepository;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepositoryEntry;

import org.springframework.stereotype.Component;

@Component
public class EvaluationSnapshotFactory {

	public EvaluationInputSnapshot create(Portfolio portfolio) {
		return new EvaluationInputSnapshot(
				EvaluationInputSnapshot.CURRENT_SCHEMA_VERSION,
				portfolio.getId(),
				portfolio.getVersion(),
				portfolio.getTitle(),
				portfolio.getEvaluationPurpose(),
				portfolio.getTargetLevel(),
				portfolio.getEvaluationAreas(),
				portfolio.getOwner().getLogin(),
				portfolio.getRepositories().stream()
						.map(this::repositorySnapshot)
						.toList()
		);
	}

	private EvaluationInputSnapshot.RepositorySnapshot repositorySnapshot(
			PortfolioRepositoryEntry entry
	) {
		GithubRepository repository = entry.getGithubRepository();
		return new EvaluationInputSnapshot.RepositorySnapshot(
				repository.getGithubId(),
				repository.getFullName(),
				repository.getHtmlUrl(),
				repository.getLanguage(),
				entry.getContributionDescription(),
				entry.getRoleSummary(),
				entry.getRoles().stream()
						.map(role -> new EvaluationInputSnapshot.RoleSnapshot(
								role.getRoleType(),
								role.getParticipationLevel(),
								role.isPrimary()
						))
						.toList()
		);
	}
}

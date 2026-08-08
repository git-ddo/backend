package com.gitddo.portfolio.domain;

import com.gitddo.github.domain.GithubRepository;

import java.util.List;

public record PortfolioRepositorySelection(
		GithubRepository repository,
		String contributionDescription,
		String roleSummary,
		List<RoleSelection> roles
) {
}

package com.gitddo.portfolio.domain;

public record RoleSelection(
		RepositoryRoleType roleType,
		ParticipationLevel participationLevel,
		boolean primary
) {
}

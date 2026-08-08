package com.gitddo.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_repository_roles")
public class RepositoryRoleAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_repository_id", nullable = false)
	private PortfolioRepositoryEntry portfolioRepository;

	@Enumerated(EnumType.STRING)
	@Column(name = "role_type", nullable = false)
	private RepositoryRoleType roleType;

	@Enumerated(EnumType.STRING)
	@Column(name = "participation_level", nullable = false)
	private ParticipationLevel participationLevel;

	@Column(name = "primary_role", nullable = false)
	private boolean primary;

	protected RepositoryRoleAssignment() {
	}

	public RepositoryRoleAssignment(
			PortfolioRepositoryEntry portfolioRepository,
			RoleSelection selection
	) {
		this.portfolioRepository = portfolioRepository;
		this.roleType = selection.roleType();
		this.participationLevel = selection.participationLevel();
		this.primary = selection.primary();
	}

	public RepositoryRoleType getRoleType() {
		return roleType;
	}

	public ParticipationLevel getParticipationLevel() {
		return participationLevel;
	}

	public boolean isPrimary() {
		return primary;
	}
}

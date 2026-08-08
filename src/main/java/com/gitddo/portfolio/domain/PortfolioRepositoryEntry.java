package com.gitddo.portfolio.domain;

import com.gitddo.github.domain.GithubRepository;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Entity
@Table(name = "portfolio_repositories")
public class PortfolioRepositoryEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "repository_id", nullable = false)
	private GithubRepository githubRepository;

	@Column(name = "contribution_description", nullable = false, columnDefinition = "TEXT")
	private String contributionDescription;

	@Column(name = "role_summary", columnDefinition = "TEXT")
	private String roleSummary;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@OneToMany(mappedBy = "portfolioRepository", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<RepositoryRoleAssignment> roles = new ArrayList<>();

	protected PortfolioRepositoryEntry() {
	}

	public PortfolioRepositoryEntry(
			Portfolio portfolio,
			GithubRepository githubRepository,
			String contributionDescription,
			String roleSummary,
			int displayOrder,
			List<RoleSelection> roleSelections
	) {
		this.portfolio = portfolio;
		this.githubRepository = githubRepository;
		this.contributionDescription = requireText(
				contributionDescription,
				"저장소별 기여 설명은 필수입니다."
		);
		this.roleSummary = normalize(roleSummary);
		this.displayOrder = displayOrder;
		validateRoles(roleSelections);
		roleSelections.forEach(selection ->
				this.roles.add(new RepositoryRoleAssignment(this, selection)));
	}

	private void validateRoles(List<RoleSelection> roleSelections) {
		if (roleSelections == null || roleSelections.isEmpty()) {
			throw new IllegalArgumentException("저장소별 역할은 최소 1개여야 합니다.");
		}
		if (roleSelections.stream().anyMatch(selection ->
				selection == null
						|| selection.roleType() == null
						|| selection.participationLevel() == null)) {
			throw new IllegalArgumentException("역할 유형과 참여 수준은 필수입니다.");
		}
		long uniqueRoleCount = roleSelections.stream()
				.map(RoleSelection::roleType)
				.collect(java.util.stream.Collectors.toCollection(HashSet::new))
				.size();
		if (uniqueRoleCount != roleSelections.size()) {
			throw new IllegalArgumentException("같은 역할을 중복으로 지정할 수 없습니다.");
		}
		if (roleSelections.stream().filter(RoleSelection::primary).count() != 1) {
			throw new IllegalArgumentException("대표 역할은 정확히 1개여야 합니다.");
		}
	}

	private String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public GithubRepository getGithubRepository() {
		return githubRepository;
	}

	public String getContributionDescription() {
		return contributionDescription;
	}

	public String getRoleSummary() {
		return roleSummary;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public List<RepositoryRoleAssignment> getRoles() {
		return List.copyOf(roles);
	}
}

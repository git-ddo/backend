package com.gitddo.portfolio.domain;

import com.gitddo.member.domain.GithubUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "portfolios")
public class Portfolio {

	public static final int MIN_REPOSITORIES = 1;
	public static final int MAX_REPOSITORIES = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "github_user_id", nullable = false)
	private GithubUser owner;

	@Column(nullable = false, length = 120)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "evaluation_purpose", nullable = false)
	private EvaluationPurpose evaluationPurpose;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_level")
	private TargetLevel targetLevel;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(
			name = "portfolio_evaluation_areas",
			joinColumns = @JoinColumn(name = "portfolio_id")
	)
	@Enumerated(EnumType.STRING)
	@Column(name = "evaluation_area", nullable = false)
	private Set<EvaluationArea> evaluationAreas = new LinkedHashSet<>();

	@OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("displayOrder ASC")
	private List<PortfolioRepositoryEntry> repositories = new ArrayList<>();

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Portfolio() {
	}

	public Portfolio(
			GithubUser owner,
			String title,
			EvaluationPurpose evaluationPurpose,
			TargetLevel targetLevel,
			Set<EvaluationArea> evaluationAreas,
			List<PortfolioRepositorySelection> repositorySelections
	) {
		this.owner = owner;
		this.createdAt = Instant.now();
		apply(title, evaluationPurpose, targetLevel, evaluationAreas, repositorySelections);
	}

	public void update(
			String title,
			EvaluationPurpose evaluationPurpose,
			TargetLevel targetLevel,
			Set<EvaluationArea> evaluationAreas,
			List<PortfolioRepositorySelection> repositorySelections
	) {
		apply(title, evaluationPurpose, targetLevel, evaluationAreas, repositorySelections);
	}

	private void apply(
			String title,
			EvaluationPurpose evaluationPurpose,
			TargetLevel targetLevel,
			Set<EvaluationArea> evaluationAreas,
			List<PortfolioRepositorySelection> repositorySelections
	) {
		this.title = requireTitle(title);
		if (evaluationPurpose == null) {
			throw new IllegalArgumentException("평가 목적은 필수입니다.");
		}
		this.evaluationPurpose = evaluationPurpose;
		this.targetLevel = targetLevel;
		replaceAreas(evaluationAreas);
		replaceRepositories(repositorySelections);
		this.updatedAt = Instant.now();
	}

	private void replaceAreas(Set<EvaluationArea> areas) {
		if (areas == null || areas.isEmpty() || areas.contains(null)) {
			throw new IllegalArgumentException("평가 분야는 최소 1개여야 합니다.");
		}
		this.evaluationAreas.clear();
		this.evaluationAreas.addAll(areas);
	}

	private void replaceRepositories(List<PortfolioRepositorySelection> selections) {
		if (selections == null
				|| selections.size() < MIN_REPOSITORIES
				|| selections.size() > MAX_REPOSITORIES) {
			throw new IllegalArgumentException("포트폴리오 저장소는 1개 이상 5개 이하여야 합니다.");
		}
		if (selections.stream().anyMatch(selection ->
				selection == null || selection.repository() == null)) {
			throw new IllegalArgumentException("저장소 정보는 필수입니다.");
		}
		long uniqueRepositoryCount = selections.stream()
				.map(selection -> selection.repository().getGithubId())
				.collect(java.util.stream.Collectors.toCollection(HashSet::new))
				.size();
		if (uniqueRepositoryCount != selections.size()) {
			throw new IllegalArgumentException("같은 저장소를 중복으로 추가할 수 없습니다.");
		}

		this.repositories.clear();
		for (int index = 0; index < selections.size(); index++) {
			PortfolioRepositorySelection selection = selections.get(index);
			this.repositories.add(new PortfolioRepositoryEntry(
					this,
					selection.repository(),
					selection.contributionDescription(),
					selection.roleSummary(),
					index,
					selection.roles()
			));
		}
	}

	private String requireTitle(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("포트폴리오 제목은 필수입니다.");
		}
		String normalized = value.trim();
		if (normalized.length() > 120) {
			throw new IllegalArgumentException("포트폴리오 제목은 120자 이하여야 합니다.");
		}
		return normalized;
	}

	public void delete() {
		this.deletedAt = Instant.now();
		this.updatedAt = this.deletedAt;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public EvaluationPurpose getEvaluationPurpose() {
		return evaluationPurpose;
	}

	public TargetLevel getTargetLevel() {
		return targetLevel;
	}

	public Set<EvaluationArea> getEvaluationAreas() {
		return Set.copyOf(evaluationAreas);
	}

	public List<PortfolioRepositoryEntry> getRepositories() {
		return List.copyOf(repositories);
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

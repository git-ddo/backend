package com.gitddo.github.domain;

import com.gitddo.github.client.GithubRepositoryPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "github_repositories")
public class GithubRepository {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "github_id", nullable = false, unique = true)
	private Long githubId;

	@Column(name = "owner_login", nullable = false)
	private String ownerLogin;

	@Column(nullable = false)
	private String name;

	@Column(name = "full_name", nullable = false, length = 512)
	private String fullName;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "html_url", nullable = false, length = 2048)
	private String htmlUrl;

	private String language;

	@Column(nullable = false)
	private boolean fork;

	@Column(nullable = false)
	private boolean archived;

	@Column(name = "default_branch")
	private String defaultBranch;

	@Column(name = "stargazers_count", nullable = false)
	private int stargazersCount;

	@Column(name = "forks_count", nullable = false)
	private int forksCount;

	@Column(name = "pushed_at")
	private Instant pushedAt;

	@Column(name = "github_updated_at")
	private Instant githubUpdatedAt;

	@Column(name = "synced_at", nullable = false)
	private Instant syncedAt;

	protected GithubRepository() {
	}

	public GithubRepository(GithubRepositoryPayload payload) {
		this.githubId = payload.id();
		update(payload);
	}

	public void update(GithubRepositoryPayload payload) {
		this.ownerLogin = ownerFrom(payload.fullName());
		this.name = payload.name();
		this.fullName = payload.fullName();
		this.description = payload.description();
		this.htmlUrl = payload.htmlUrl();
		this.language = payload.language();
		this.fork = payload.fork();
		this.archived = payload.archived();
		this.defaultBranch = payload.defaultBranch();
		this.stargazersCount = payload.stargazersCount();
		this.forksCount = payload.forksCount();
		this.pushedAt = payload.pushedAt();
		this.githubUpdatedAt = payload.updatedAt();
		this.syncedAt = Instant.now();
	}

	private String ownerFrom(String fullName) {
		int separator = fullName.indexOf('/');
		return separator < 0 ? fullName : fullName.substring(0, separator);
	}

	public Long getId() {
		return id;
	}

	public Long getGithubId() {
		return githubId;
	}

	public String getName() {
		return name;
	}

	public String getFullName() {
		return fullName;
	}

	public String getDescription() {
		return description;
	}

	public String getHtmlUrl() {
		return htmlUrl;
	}

	public String getLanguage() {
		return language;
	}

	public boolean isFork() {
		return fork;
	}

	public boolean isArchived() {
		return archived;
	}

	public String getDefaultBranch() {
		return defaultBranch;
	}

	public int getStargazersCount() {
		return stargazersCount;
	}

	public int getForksCount() {
		return forksCount;
	}

	public Instant getPushedAt() {
		return pushedAt;
	}

	public Instant getGithubUpdatedAt() {
		return githubUpdatedAt;
	}
}

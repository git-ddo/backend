package com.gitddo.github.domain;

import com.gitddo.member.domain.GithubUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "github_user_repository_accesses")
public class UserRepositoryAccess {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "github_user_id", nullable = false)
	private GithubUser githubUser;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "repository_id", nullable = false)
	private GithubRepository githubRepository;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "first_seen_at", nullable = false, updatable = false)
	private Instant firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	protected UserRepositoryAccess() {
	}

	public UserRepositoryAccess(GithubUser githubUser, GithubRepository githubRepository) {
		this.githubUser = githubUser;
		this.githubRepository = githubRepository;
		this.active = true;
		this.firstSeenAt = Instant.now();
		this.lastSeenAt = this.firstSeenAt;
	}

	public void markSeen() {
		this.active = true;
		this.lastSeenAt = Instant.now();
	}

	public void deactivate() {
		this.active = false;
	}

	public Long getRepositoryId() {
		return githubRepository.getId();
	}
}

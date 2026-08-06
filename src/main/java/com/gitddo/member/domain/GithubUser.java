package com.gitddo.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "github_users")
public class GithubUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "github_id", nullable = false, unique = true)
	private Long githubId;

	@Column(nullable = false)
	private String login;

	@Column(name = "avatar_url", length = 2048)
	private String avatarUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GithubUser() {
	}

	public GithubUser(Long githubId, String login, String avatarUrl) {
		this.githubId = githubId;
		this.login = login;
		this.avatarUrl = avatarUrl;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getGithubId() {
		return githubId;
	}

	public String getLogin() {
		return login;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void updateProfile(String login, String avatarUrl) {
		this.login = login;
		this.avatarUrl = avatarUrl;
		this.updatedAt = Instant.now();
	}
}

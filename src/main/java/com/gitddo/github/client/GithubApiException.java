package com.gitddo.github.client;

import org.springframework.http.HttpStatusCode;

public class GithubApiException extends RuntimeException {

	private final HttpStatusCode githubStatus;
	private final String rateLimitRemaining;

	public GithubApiException(
			HttpStatusCode githubStatus,
			String rateLimitRemaining,
			String message,
			Throwable cause
	) {
		super(message, cause);
		this.githubStatus = githubStatus;
		this.rateLimitRemaining = rateLimitRemaining;
	}

	public GithubApiException(String message) {
		super(message);
		this.githubStatus = null;
		this.rateLimitRemaining = null;
	}

	public HttpStatusCode getGithubStatus() {
		return githubStatus;
	}

	public String getRateLimitRemaining() {
		return rateLimitRemaining;
	}
}

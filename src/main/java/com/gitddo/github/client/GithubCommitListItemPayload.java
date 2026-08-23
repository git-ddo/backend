package com.gitddo.github.client;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record GithubCommitListItemPayload(
		String sha,
		CommitPayload commit,
		UserPayload author,
		List<ParentPayload> parents
) {

	public GithubCommitListItemPayload {
		parents = parents == null ? List.of() : List.copyOf(parents);
	}

	public boolean isMerge() {
		if (parents.size() >= 2) {
			return true;
		}
		String firstLine = firstMessageLine();
		if (firstLine.isEmpty()) {
			return false;
		}
		String normalized = firstLine.toLowerCase(Locale.ROOT);
		return normalized.startsWith("merge pull request")
				|| normalized.startsWith("merge branch")
				|| normalized.startsWith("merge remote-tracking");
	}

	private String firstMessageLine() {
		if (commit == null || commit.message() == null || commit.message().isBlank()) {
			return "";
		}
		int newline = commit.message().indexOf('\n');
		String line = newline < 0 ? commit.message() : commit.message().substring(0, newline);
		return line.strip();
	}

	public record CommitPayload(
			String message,
			GitUserPayload author
	) {
	}

	public record GitUserPayload(
			String name,
			Instant date
	) {
	}

	public record UserPayload(
			String login
	) {
	}

	public record ParentPayload(
			String sha
	) {
	}
}

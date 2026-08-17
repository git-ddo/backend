package com.gitddo.github.client;

public record GithubCommitSnapshotPayload(
		String sha,
		CommitPayload commit
) {

	public String treeSha() {
		return commit == null || commit.tree() == null
				? null
				: commit.tree().sha();
	}

	public record CommitPayload(TreePayload tree) {
	}

	public record TreePayload(String sha) {
	}
}

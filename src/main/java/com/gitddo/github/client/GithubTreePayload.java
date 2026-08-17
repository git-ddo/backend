package com.gitddo.github.client;

import java.util.List;

public record GithubTreePayload(
		String sha,
		boolean truncated,
		List<GithubTreeEntryPayload> tree
) {

	public GithubTreePayload {
		tree = tree == null ? List.of() : List.copyOf(tree);
	}
}

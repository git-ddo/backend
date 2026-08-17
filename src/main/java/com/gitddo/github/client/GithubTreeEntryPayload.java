package com.gitddo.github.client;

public record GithubTreeEntryPayload(
		String path,
		String mode,
		String type,
		String sha,
		Long size,
		String url
) {

	public boolean isBlob() {
		return "blob".equals(type);
	}
}

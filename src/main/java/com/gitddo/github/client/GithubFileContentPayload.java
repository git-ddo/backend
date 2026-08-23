package com.gitddo.github.client;

public record GithubFileContentPayload(
		String type,
		String encoding,
		Long size,
		String path,
		String sha,
		String content
) {
}

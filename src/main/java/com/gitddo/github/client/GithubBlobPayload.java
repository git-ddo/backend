package com.gitddo.github.client;

public record GithubBlobPayload(
		String sha,
		Long size,
		String encoding,
		String content
) {
}

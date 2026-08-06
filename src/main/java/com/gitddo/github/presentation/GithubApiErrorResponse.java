package com.gitddo.github.presentation;

public record GithubApiErrorResponse(
		String code,
		String message
) {
}

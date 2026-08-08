package com.gitddo.github.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "동기화된 GitHub 저장소 검색 결과")
public record GithubRepositorySearchResponse(
		@Schema(description = "검색된 저장소 수", example = "3")
		int totalCount,
		List<GithubRepositoryResponse> repositories
) {
}

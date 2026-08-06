package com.gitddo.github.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "로그인 사용자가 소유한 public 저장소 목록")
public record GithubRepositoriesResponse(
		@Schema(description = "조회된 전체 저장소 수", example = "27")
		int totalCount,
		@Schema(description = "GitHub API의 남은 요청 횟수", example = "4998")
		Integer rateLimitRemaining,
		List<GithubRepositoryResponse> repositories
) {
}

package com.gitddo.member.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 로그인한 GitHub 사용자")
public record CurrentUserResponse(
		@Schema(description = "변경되지 않는 GitHub 사용자 ID", example = "225514409")
		Long githubId,
		@Schema(description = "GitHub 로그인 이름", example = "jhkim2da")
		String login,
		@Schema(
				description = "GitHub 프로필 이미지 URL",
				example = "https://avatars.githubusercontent.com/u/225514409?v=4"
		)
		String avatarUrl
) {
}

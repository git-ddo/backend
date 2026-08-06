package com.gitddo.github.presentation;

import com.gitddo.github.client.GithubApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GithubApiExceptionHandler {

	@ExceptionHandler(GithubApiException.class)
	ResponseEntity<GithubApiErrorResponse> handleGithubApiException(GithubApiException exception) {
		if ("0".equals(exception.getRateLimitRemaining())) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new GithubApiErrorResponse(
							"GITHUB_RATE_LIMIT_EXCEEDED",
							"GitHub API 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
					));
		}

		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new GithubApiErrorResponse(
						"GITHUB_API_ERROR",
						"GitHub에서 저장소 정보를 가져오지 못했습니다."
				));
	}
}

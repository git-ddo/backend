package com.gitddo.github.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class GithubApiRetry {

	private static final Logger log = LoggerFactory.getLogger(GithubApiRetry.class);

	private final int maxAttempts;
	private final Duration retryDelay;

	public GithubApiRetry(
			@Value("${gitddo.github.max-attempts:3}") int maxAttempts,
			@Value("${gitddo.github.retry-delay:1s}") Duration retryDelay
	) {
		this.maxAttempts = Math.max(1, maxAttempts);
		this.retryDelay = retryDelay == null ? Duration.ZERO : retryDelay;
	}

	static GithubApiRetry noRetry() {
		return new GithubApiRetry(1, Duration.ZERO);
	}

	<T> T execute(String operation, Supplier<T> action) {
		GithubApiException lastFailure = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return action.get();
			} catch (GithubApiException exception) {
				lastFailure = exception;
				if (!isRetryable(exception) || attempt == maxAttempts) {
					throw exception;
				}
				log.warn(
						"GitHub {} failed (attempt {}/{}): {}",
						operation,
						attempt,
						maxAttempts,
						exception.getMessage()
				);
				sleep();
			} catch (RestClientException exception) {
				lastFailure = new GithubApiException(
						null,
						null,
						operation + " 호출에 실패했습니다.",
						exception
				);
				if (attempt == maxAttempts) {
					throw lastFailure;
				}
				log.warn(
						"GitHub {} network error (attempt {}/{}): {}",
						operation,
						attempt,
						maxAttempts,
						exception.getMessage()
				);
				sleep();
			}
		}
		throw lastFailure;
	}

	static boolean isRetryable(GithubApiException exception) {
		HttpStatusCode status = exception.getGithubStatus();
		if (status == null) {
			return exception.getCause() instanceof RestClientException;
		}
		return status.value() == 429 || status.is5xxServerError();
	}

	private void sleep() {
		if (retryDelay.isZero() || retryDelay.isNegative()) {
			return;
		}
		try {
			Thread.sleep(retryDelay.toMillis());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new GithubApiException("GitHub API 재시도 대기 중 중단되었습니다.");
		}
	}
}

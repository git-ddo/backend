package com.gitddo.github.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubApiRetryTests {

	@Test
	void retriesUntilSuccess() {
		GithubApiRetry retry = new GithubApiRetry(3, Duration.ZERO);
		AtomicInteger attempts = new AtomicInteger();

		String result = retry.execute("languages", () -> {
			if (attempts.incrementAndGet() < 2) {
				throw new GithubApiException(
						HttpStatus.BAD_GATEWAY,
						"10",
						"temporary",
						null
				);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(attempts.get()).isEqualTo(2);
	}

	@Test
	void doesNotRetryForbidden() {
		GithubApiRetry retry = new GithubApiRetry(3, Duration.ZERO);
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> retry.execute("languages", () -> {
			attempts.incrementAndGet();
			throw new GithubApiException(HttpStatus.FORBIDDEN, "0", "denied", null);
		})).isInstanceOf(GithubApiException.class);

		assertThat(attempts.get()).isEqualTo(1);
	}

	@Test
	void retriesNetworkErrors() {
		GithubApiRetry retry = new GithubApiRetry(2, Duration.ZERO);
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> retry.execute("languages", () -> {
			attempts.incrementAndGet();
			throw new ResourceAccessException("timeout");
		})).isInstanceOf(GithubApiException.class);

		assertThat(attempts.get()).isEqualTo(2);
	}
}

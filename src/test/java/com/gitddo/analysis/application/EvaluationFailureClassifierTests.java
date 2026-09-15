package com.gitddo.analysis.application;

import com.gitddo.analysis.client.AiAnalysisClientException;
import com.gitddo.analysis.contract.AiErrorCode;
import com.gitddo.analysis.domain.EvaluationFailureCode;
import com.gitddo.github.client.GithubApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationFailureClassifierTests {

	private final EvaluationFailureClassifier classifier = new EvaluationFailureClassifier();

	@Test
	void classifiesGithubRateLimit() {
		GithubApiException exception = new GithubApiException(
				HttpStatus.FORBIDDEN,
				"0",
				"rate limit",
				null
		);
		assertThat(classifier.classify(exception)).isEqualTo(EvaluationFailureCode.GITHUB_RATE_LIMIT);
	}

	@Test
	void classifiesGithubApiError() {
		GithubApiException exception = new GithubApiException(
				HttpStatus.BAD_GATEWAY,
				"40",
				"github down",
				null
		);
		assertThat(classifier.classify(exception)).isEqualTo(EvaluationFailureCode.GITHUB_API_ERROR);
	}

	@Test
	void classifiesInvalidAiResponse() {
		assertThat(classifier.classify(new InvalidAiAnalysisResponseException("bad id")))
				.isEqualTo(EvaluationFailureCode.AI_INVALID_RESPONSE);
	}

	@Test
	void classifiesAiRateLimit() {
		AiAnalysisClientException exception = new AiAnalysisClientException(
				"limited",
				null,
				AiErrorCode.LLM_RATE_LIMITED,
				true
		);
		assertThat(classifier.classify(exception)).isEqualTo(EvaluationFailureCode.AI_RATE_LIMITED);
	}

	@Test
	void classifiesUnknownAsEvaluationFailed() {
		assertThat(classifier.classify(new IllegalStateException("broken")))
				.isEqualTo(EvaluationFailureCode.EVALUATION_FAILED);
	}
}

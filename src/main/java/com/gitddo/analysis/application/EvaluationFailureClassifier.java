package com.gitddo.analysis.application;

import com.gitddo.analysis.client.AiAnalysisClientException;
import com.gitddo.analysis.contract.AiErrorCode;
import com.gitddo.analysis.domain.EvaluationFailureCode;
import com.gitddo.github.client.GithubApiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class EvaluationFailureClassifier {

	public EvaluationFailureCode classify(Exception exception) {
		if (exception instanceof InvalidAiAnalysisResponseException) {
			return EvaluationFailureCode.AI_INVALID_RESPONSE;
		}
		if (exception instanceof AiAnalysisClientException aiException) {
			return classifyAi(aiException);
		}
		if (exception instanceof GithubApiException githubException) {
			return classifyGithub(githubException);
		}
		return EvaluationFailureCode.EVALUATION_FAILED;
	}

	private EvaluationFailureCode classifyAi(AiAnalysisClientException exception) {
		AiErrorCode code = exception.code();
		if (code == AiErrorCode.LLM_RATE_LIMITED) {
			return EvaluationFailureCode.AI_RATE_LIMITED;
		}
		if (code == AiErrorCode.LLM_TIMEOUT) {
			return EvaluationFailureCode.AI_TIMEOUT;
		}
		if (code == AiErrorCode.STRUCTURED_OUTPUT_INVALID) {
			return EvaluationFailureCode.AI_INVALID_RESPONSE;
		}
		return EvaluationFailureCode.AI_SERVER_ERROR;
	}

	private EvaluationFailureCode classifyGithub(GithubApiException exception) {
		if ("0".equals(exception.getRateLimitRemaining())) {
			return EvaluationFailureCode.GITHUB_RATE_LIMIT;
		}
		HttpStatusCode status = exception.getGithubStatus();
		if (status != null && status.value() == 429) {
			return EvaluationFailureCode.GITHUB_RATE_LIMIT;
		}
		return EvaluationFailureCode.GITHUB_API_ERROR;
	}
}

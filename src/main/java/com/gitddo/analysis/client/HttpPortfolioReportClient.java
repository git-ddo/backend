package com.gitddo.analysis.client;

import com.gitddo.analysis.contract.AiAnalysisError;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "gitddo.ai.mode", havingValue = "http")
public class HttpPortfolioReportClient implements PortfolioReportClient {

	private static final Logger log = LoggerFactory.getLogger(HttpPortfolioReportClient.class);

	private final RestClient aiRestClient;
	private final ObjectMapper objectMapper;
	private final String reportsPath;
	private final int maxAttempts;
	private final Duration retryDelay;

	public HttpPortfolioReportClient(
			@Qualifier("aiRestClient") RestClient aiRestClient,
			ObjectMapper objectMapper,
			@Value("${gitddo.ai.reports-path:/internal/v1/portfolio-reports}") String reportsPath,
			@Value("${gitddo.ai.max-attempts:3}") int maxAttempts,
			@Value("${gitddo.ai.retry-delay:2s}") Duration retryDelay
	) {
		this.aiRestClient = aiRestClient;
		this.objectMapper = objectMapper;
		this.reportsPath = reportsPath;
		this.maxAttempts = Math.max(1, maxAttempts);
		this.retryDelay = retryDelay == null ? Duration.ZERO : retryDelay;
	}

	HttpPortfolioReportClient(
			RestClient aiRestClient,
			ObjectMapper objectMapper,
			String reportsPath
	) {
		this(aiRestClient, objectMapper, reportsPath, 1, Duration.ZERO);
	}

	@Override
	public AiAnalysisResponse requestReport(AiAnalysisRequest request) {
		AiAnalysisClientException lastFailure = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return postOnce(request);
			} catch (AiAnalysisClientException exception) {
				lastFailure = exception;
				if (!shouldRetry(exception) || attempt == maxAttempts) {
					throw exception;
				}
				log.warn(
						"AI report request failed (attempt {}/{}): {}",
						attempt,
						maxAttempts,
						exception.getMessage()
				);
				sleep(retryDelay);
			}
		}
		throw lastFailure;
	}

	private AiAnalysisResponse postOnce(AiAnalysisRequest request) {
		try {
			AiAnalysisResponse response = aiRestClient.post()
					.uri(reportsPath)
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(AiAnalysisResponse.class);
			if (response == null) {
				throw new AiAnalysisClientException("AI 서버 응답이 비어 있습니다.");
			}
			return response;
		} catch (RestClientResponseException exception) {
			throw toClientException(exception);
		} catch (RestClientException exception) {
			throw new AiAnalysisClientException("AI 서버 호출에 실패했습니다.", exception, null, true);
		}
	}

	private boolean shouldRetry(AiAnalysisClientException exception) {
		return Boolean.TRUE.equals(exception.retryable());
	}

	private void sleep(Duration delay) {
		if (delay.isZero() || delay.isNegative()) {
			return;
		}
		try {
			Thread.sleep(delay.toMillis());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AiAnalysisClientException("AI 서버 재시도 대기 중 중단되었습니다.", interrupted);
		}
	}

	private AiAnalysisClientException toClientException(RestClientResponseException exception) {
		String body = exception.getResponseBodyAsString();
		if (body != null && !body.isBlank()) {
			try {
				AiAnalysisError error = objectMapper.readValue(body, AiAnalysisError.class);
				if (error.code() != null) {
					return new AiAnalysisClientException(
							error.code() + ": " + error.message() + " (retryable=" + error.retryable() + ")",
							exception,
							error.code(),
							error.retryable()
					);
				}
			} catch (JsonProcessingException ignored) {
				// fall through to status code
			}
		}
		HttpStatusCode status = exception.getStatusCode();
		boolean retryable = status.is5xxServerError() || status.value() == 429;
		return new AiAnalysisClientException(
				"AI 서버가 " + status.value() + "를 반환했습니다.",
				exception,
				null,
				retryable
		);
	}
}

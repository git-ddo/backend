package com.gitddo.analysis.client;

import com.gitddo.analysis.contract.AiAnalysisError;
import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "gitddo.ai.mode", havingValue = "http")
public class HttpPortfolioReportClient implements PortfolioReportClient {

	private final RestClient aiRestClient;
	private final ObjectMapper objectMapper;
	private final String reportsPath;

	public HttpPortfolioReportClient(
			@Qualifier("aiRestClient") RestClient aiRestClient,
			ObjectMapper objectMapper,
			@Value("${gitddo.ai.reports-path:/internal/v1/portfolio-reports}") String reportsPath
	) {
		this.aiRestClient = aiRestClient;
		this.objectMapper = objectMapper;
		this.reportsPath = reportsPath;
	}

	@Override
	public AiAnalysisResponse requestReport(AiAnalysisRequest request) {
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
			throw new AiAnalysisClientException("AI 서버 호출에 실패했습니다.", exception);
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
		return new AiAnalysisClientException(
				"AI 서버가 " + exception.getStatusCode().value() + "를 반환했습니다.",
				exception
		);
	}
}

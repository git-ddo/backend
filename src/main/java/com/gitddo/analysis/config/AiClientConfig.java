package com.gitddo.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiClientConfig {

	@Bean(name = "aiRestClient")
	@ConditionalOnProperty(name = "gitddo.ai.mode", havingValue = "http")
	RestClient aiRestClient(
			@Value("${gitddo.ai.base-url:}") String baseUrl,
			@Value("${gitddo.ai.api-key:}") String apiKey,
			@Value("${gitddo.ai.api-key-header:X-Gitddo-Ai-Key}") String apiKeyHeader,
			@Value("${gitddo.ai.connect-timeout:5s}") Duration connectTimeout,
			@Value("${gitddo.ai.read-timeout:600s}") Duration readTimeout
	) {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("gitddo.ai.mode=http 일 때 gitddo.ai.base-url이 필요합니다.");
		}
		String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder()
						.connectTimeout(connectTimeout)
						.build()
		);
		requestFactory.setReadTimeout(readTimeout);
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(normalized)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		if (apiKey != null && !apiKey.isBlank()) {
			builder.defaultHeader(apiKeyHeader, apiKey);
		}
		return builder.build();
	}
}

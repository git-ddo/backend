package com.gitddo.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

	@Bean(name = "aiRestClient")
	@ConditionalOnProperty(name = "gitddo.ai.mode", havingValue = "http")
	RestClient aiRestClient(@Value("${gitddo.ai.base-url:}") String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("gitddo.ai.mode=http 일 때 gitddo.ai.base-url이 필요합니다.");
		}
		String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return RestClient.builder()
				.baseUrl(normalized)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}
}

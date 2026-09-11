package com.gitddo.github.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class GithubApiConfig {

	@Bean
	RestClient githubRestClient(
			@Value("${gitddo.github.connect-timeout:5s}") Duration connectTimeout,
			@Value("${gitddo.github.read-timeout:30s}") Duration readTimeout
	) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder()
						.connectTimeout(connectTimeout)
						.build()
		);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
				.baseUrl("https://api.github.com")
				.requestFactory(requestFactory)
				.defaultHeader("Accept", "application/vnd.github+json")
				.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
				.build();
	}
}

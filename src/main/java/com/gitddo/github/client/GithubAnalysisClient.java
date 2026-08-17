package com.gitddo.github.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

@Component
public class GithubAnalysisClient {

	private final RestClient githubRestClient;

	public GithubAnalysisClient(RestClient githubRestClient) {
		this.githubRestClient = githubRestClient;
	}

	public GithubRepositoryPayload fetchRepository(
			String accessToken,
			String owner,
			String repository
	) {
		return get(
				accessToken,
				uriBuilder -> uriBuilder
						.pathSegment("repos", owner, repository)
						.build(),
				new ParameterizedTypeReference<>() {
				},
				"GitHub 저장소 기본 정보 조회에 실패했습니다."
		);
	}

	public GithubCommitSnapshotPayload fetchCommitSnapshot(
			String accessToken,
			String owner,
			String repository,
			String ref
	) {
		return get(
				accessToken,
				uriBuilder -> uriBuilder
						.pathSegment("repos", owner, repository, "commits", ref)
						.build(),
				new ParameterizedTypeReference<>() {
				},
				"GitHub 저장소 Snapshot SHA 조회에 실패했습니다."
		);
	}

	public Map<String, Long> fetchLanguages(
			String accessToken,
			String owner,
			String repository
	) {
		return Map.copyOf(get(
				accessToken,
				uriBuilder -> uriBuilder
						.pathSegment("repos", owner, repository, "languages")
						.build(),
				new ParameterizedTypeReference<>() {
				},
				"GitHub 저장소 언어 정보 조회에 실패했습니다."
		));
	}

	public GithubTreePayload fetchTree(
			String accessToken,
			String owner,
			String repository,
			String treeSha
	) {
		return get(
				accessToken,
				uriBuilder -> uriBuilder
						.pathSegment("repos", owner, repository, "git", "trees", treeSha)
						.queryParam("recursive", 1)
						.build(),
				new ParameterizedTypeReference<>() {
				},
				"GitHub 저장소 파일 트리 조회에 실패했습니다."
		);
	}

	public GithubBlobPayload fetchBlob(
			String accessToken,
			String owner,
			String repository,
			String blobSha
	) {
		return get(
				accessToken,
				uriBuilder -> uriBuilder
						.pathSegment("repos", owner, repository, "git", "blobs", blobSha)
						.build(),
				new ParameterizedTypeReference<>() {
				},
				"GitHub 저장소 파일 내용 조회에 실패했습니다."
		);
	}

	private <T> T get(
			String accessToken,
			Function<UriBuilder, URI> uriFunction,
			ParameterizedTypeReference<T> responseType,
			String failureMessage
	) {
		try {
			T body = githubRestClient.get()
					.uri(uriFunction)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve()
					.body(responseType);
			if (body == null) {
				throw new GithubApiException(failureMessage + " GitHub 응답이 비어 있습니다.");
			}
			return body;
		} catch (RestClientResponseException exception) {
			throw new GithubApiException(
					exception.getStatusCode(),
					exception.getResponseHeaders() == null
							? null
							: exception.getResponseHeaders()
									.getFirst("X-RateLimit-Remaining"),
					failureMessage,
					exception
			);
		}
	}
}

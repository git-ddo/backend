package com.gitddo.github.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Component
public class GithubRepositoryClient {

	private static final int PER_PAGE = 100;
	private static final int MAX_PAGES = 100;

	private final RestClient githubRestClient;

	public GithubRepositoryClient(RestClient githubRestClient) {
		this.githubRestClient = githubRestClient;
	}

	public GithubRepositoryFetchResult fetchParticipatingPublicRepositories(String accessToken) {
		List<GithubRepositoryPayload> repositories = new ArrayList<>();
		Integer rateLimitRemaining = null;

		for (int page = 1; page <= MAX_PAGES; page++) {
			ResponseEntity<List<GithubRepositoryPayload>> response = fetchPage(accessToken, page);
			List<GithubRepositoryPayload> pageItems = response.getBody();

			if (pageItems != null) {
				repositories.addAll(pageItems);
			}

			rateLimitRemaining = parseRateLimitRemaining(
					response.getHeaders().getFirst("X-RateLimit-Remaining")
			);

			if (!hasNextPage(response.getHeaders())) {
				return new GithubRepositoryFetchResult(
						List.copyOf(repositories),
						rateLimitRemaining
				);
			}
		}

		throw new GithubApiException("GitHub 저장소 페이지 수가 안전 제한을 초과했습니다.");
	}

	private ResponseEntity<List<GithubRepositoryPayload>> fetchPage(String accessToken, int page) {
		try {
			return githubRestClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/user/repos")
							.queryParam("visibility", "public")
							.queryParam("affiliation", "owner,collaborator,organization_member")
							.queryParam("per_page", PER_PAGE)
							.queryParam("page", page)
							.queryParam("sort", "updated")
							.queryParam("direction", "desc")
							.build()
					)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve()
					.toEntity(new ParameterizedTypeReference<>() {
					});
		} catch (RestClientResponseException exception) {
			throw new GithubApiException(
					exception.getStatusCode(),
					exception.getResponseHeaders() == null
							? null
							: exception.getResponseHeaders().getFirst("X-RateLimit-Remaining"),
					"GitHub 저장소 조회에 실패했습니다.",
					exception
			);
		}
	}

	private boolean hasNextPage(HttpHeaders headers) {
		return headers.getOrEmpty(HttpHeaders.LINK).stream()
				.anyMatch(link -> link.contains("rel=\"next\""));
	}

	private Integer parseRateLimitRemaining(String value) {
		if (value == null) {
			return null;
		}

		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}

package com.gitddo.github.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GithubRepositoryClientTests {

	private MockRestServiceServer server;
	private GithubRepositoryClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GithubRepositoryClient(
				builder.baseUrl("https://api.github.com").build()
		);
	}

	@Test
	void fetchesEveryRepositoryPage() {
		server.expect(requestTo(
						"https://api.github.com/user/repos?visibility=public"
								+ "&affiliation=owner,collaborator,organization_member"
								+ "&per_page=100&page=1&sort=updated&direction=desc"
				))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andRespond(withSuccess(repositoryJson(1L, "first"), MediaType.APPLICATION_JSON)
						.header(HttpHeaders.LINK,
								"<https://api.github.com/user/repos?page=2>; rel=\"next\"")
						.header("X-RateLimit-Remaining", "4999"));

		server.expect(requestTo(
						"https://api.github.com/user/repos?visibility=public"
								+ "&affiliation=owner,collaborator,organization_member"
								+ "&per_page=100&page=2&sort=updated&direction=desc"
				))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andRespond(withSuccess(repositoryJson(2L, "second"), MediaType.APPLICATION_JSON)
						.header("X-RateLimit-Remaining", "4998"));

		GithubRepositoryFetchResult result =
				client.fetchParticipatingPublicRepositories("test-token");

		assertThat(result.repositories())
				.extracting(GithubRepositoryPayload::name)
				.containsExactly("first", "second");
		assertThat(result.rateLimitRemaining()).isEqualTo(4998);
		server.verify();
	}

	private String repositoryJson(long id, String name) {
		return """
				[
				  {
				    "id": %d,
				    "name": "%s",
				    "full_name": "owner/%s",
				    "description": "repository",
				    "html_url": "https://github.com/owner/%s",
				    "language": "Java",
				    "fork": false,
				    "archived": false,
				    "default_branch": "main",
				    "stargazers_count": 3,
				    "forks_count": 1,
				    "pushed_at": "2026-08-01T00:00:00Z",
				    "updated_at": "2026-08-02T00:00:00Z"
				  }
				]
				""".formatted(id, name, name, name);
	}
}

package com.gitddo.github.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GithubAnalysisClientTests {

	private MockRestServiceServer server;
	private RestClient restClient;
	private GithubAnalysisClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.baseUrl("https://api.github.com").build();
		client = new GithubAnalysisClient(restClient);
	}

	@Test
	void fetchesP0RepositorySourcesAtFixedSnapshot() {
		expectJson(
				"https://api.github.com/repos/git-ddo/backend",
				"""
						{
						  "id": 123,
						  "name": "backend",
						  "full_name": "git-ddo/backend",
						  "html_url": "https://github.com/git-ddo/backend",
						  "language": "Java",
						  "fork": false,
						  "archived": false,
						  "default_branch": "main",
						  "stargazers_count": 1,
						  "forks_count": 0
						}
						"""
		);
		expectJson(
				"https://api.github.com/repos/git-ddo/backend/commits/main",
				"""
						{
						  "sha": "commit-sha",
						  "commit": {
						    "tree": {
						      "sha": "tree-sha"
						    }
						  }
						}
						"""
		);
		expectJson(
				"https://api.github.com/repos/git-ddo/backend/languages",
				"""
						{
						  "Java": 12000,
						  "SQL": 500
						}
						"""
		);
		expectJson(
				"https://api.github.com/repos/git-ddo/backend/git/trees/tree-sha?recursive=1",
				"""
						{
						  "sha": "tree-sha",
						  "truncated": false,
						  "tree": [
						    {
						      "path": "README.md",
						      "mode": "100644",
						      "type": "blob",
						      "sha": "blob-sha",
						      "size": 128,
						      "url": "https://api.github.com/repos/git-ddo/backend/git/blobs/blob-sha"
						    }
						  ]
						}
						"""
		);
		expectJson(
				"https://api.github.com/repos/git-ddo/backend/git/blobs/blob-sha",
				"""
						{
						  "sha": "blob-sha",
						  "size": 128,
						  "encoding": "base64",
						  "content": "IyBHaXRkZG8="
						}
						"""
		);

		GithubRepositoryPayload repository =
				client.fetchRepository("test-token", "git-ddo", "backend");
		GithubCommitSnapshotPayload snapshot =
				client.fetchCommitSnapshot("test-token", "git-ddo", "backend", "main");
		Map<String, Long> languages =
				client.fetchLanguages("test-token", "git-ddo", "backend");
		GithubTreePayload tree =
				client.fetchTree("test-token", "git-ddo", "backend", snapshot.treeSha());
		GithubBlobPayload blob =
				client.fetchBlob("test-token", "git-ddo", "backend", tree.tree().getFirst().sha());

		assertThat(repository.fullName()).isEqualTo("git-ddo/backend");
		assertThat(snapshot.sha()).isEqualTo("commit-sha");
		assertThat(snapshot.treeSha()).isEqualTo("tree-sha");
		assertThat(languages).containsEntry("Java", 12000L);
		assertThat(tree.truncated()).isFalse();
		assertThat(tree.tree().getFirst().isBlob()).isTrue();
		assertThat(blob.content()).isEqualTo("IyBHaXRkZG8=");
		server.verify();
	}

	@Test
	void fetchesAuthorCommitsAndPullRequests() {
		expectJson(
				"https://api.github.com/repos/git-ddo/backend/commits?author=git-ddo-user&sha=commit-sha&per_page=21",
				"""
						[
						  {
						    "sha": "abc123",
						    "commit": {
						      "message": "Add filter",
						      "author": {
						        "name": "Kim",
						        "date": "2026-08-01T00:00:00Z"
						      }
						    },
						    "author": { "login": "git-ddo-user" }
						  }
						]
						"""
		);
		expectJson(
				"https://api.github.com/repos/git-ddo/backend/pulls?state=all&sort=updated&direction=desc&per_page=30",
				"""
						[
						  {
						    "number": 12,
						    "title": "Add auth filter",
						    "state": "closed",
						    "user": { "login": "git-ddo-user" },
						    "head": { "sha": "def456" },
						    "created_at": "2026-08-01T00:00:00Z",
						    "merged_at": "2026-08-02T00:00:00Z"
						  }
						]
						"""
		);

		assertThat(client.fetchCommits(
				"test-token",
				"git-ddo",
				"backend",
				"git-ddo-user",
				"commit-sha",
				21
		)).singleElement().satisfies(commit -> assertThat(commit.sha()).isEqualTo("abc123"));
		assertThat(client.fetchPullRequests("test-token", "git-ddo", "backend", 30))
				.singleElement()
				.satisfies(pullRequest -> {
					assertThat(pullRequest.number()).isEqualTo(12);
					assertThat(pullRequest.authoredBy("git-ddo-user")).isTrue();
				});
		server.verify();
	}

	@Test
	void retriesRetryableGithubFailuresThenSucceeds() {
		GithubAnalysisClient retryingClient = new GithubAnalysisClient(
				restClient,
				new GithubApiRetry(3, Duration.ZERO)
		);
		server.expect(requestTo("https://api.github.com/repos/git-ddo/backend/languages"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
						.header("X-RateLimit-Remaining", "40"));
		server.expect(requestTo("https://api.github.com/repos/git-ddo/backend/languages"))
				.andRespond(withSuccess("""
						{
						  "Java": 12000
						}
						""", MediaType.APPLICATION_JSON));

		assertThat(retryingClient.fetchLanguages("test-token", "git-ddo", "backend"))
				.containsEntry("Java", 12000L);
		server.verify();
	}

	@Test
	void doesNotRetryClientErrors() {
		server.expect(requestTo("https://api.github.com/repos/git-ddo/backend/languages"))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andRespond(withStatus(HttpStatus.FORBIDDEN)
						.header("X-RateLimit-Remaining", "0"));

		assertThatThrownBy(() ->
				client.fetchLanguages("test-token", "git-ddo", "backend"))
				.isInstanceOfSatisfying(GithubApiException.class, exception -> {
					assertThat(exception.getGithubStatus())
							.isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(exception.getRateLimitRemaining()).isEqualTo("0");
				});
		server.verify();
	}

	private void expectJson(String url, String responseBody) {
		server.expect(requestTo(url))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
	}
}

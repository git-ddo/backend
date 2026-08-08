package com.gitddo;

import com.gitddo.github.client.GithubRepositoryPayload;
import com.gitddo.github.domain.GithubRepository;
import com.gitddo.github.domain.GithubRepositoryRepository;
import com.gitddo.github.domain.UserRepositoryAccess;
import com.gitddo.github.domain.UserRepositoryAccessRepository;
import com.gitddo.member.domain.GithubUser;
import com.gitddo.member.domain.GithubUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class GitddoApplicationTests {

	@Autowired
	private GithubUserRepository githubUserRepository;

	@Autowired
	private GithubRepositoryRepository githubRepositoryRepository;

	@Autowired
	private UserRepositoryAccessRepository userRepositoryAccessRepository;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void savesGithubUserToPostgres() {
		GithubUser savedUser = githubUserRepository.save(
				new GithubUser(123456L, "gitddo-user", "https://avatars.githubusercontent.com/u/123456")
		);

		assertThat(githubUserRepository.findByGithubId(123456L))
				.isPresent()
				.get()
				.extracting(GithubUser::getId, GithubUser::getLogin)
				.containsExactly(savedUser.getId(), "gitddo-user");
	}

	@Test
	void returnsAuthenticatedGithubUser() throws Exception {
		mockMvc.perform(get("/api/v1/me")
						.with(oauth2Login().attributes(attributes -> {
							attributes.put("id", 987654L);
							attributes.put("login", "authenticated-user");
							attributes.put("avatar_url", "https://avatars.githubusercontent.com/u/987654");
						})))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.githubId").value(987654L))
				.andExpect(jsonPath("$.login").value("authenticated-user"))
				.andExpect(jsonPath("$.avatarUrl")
						.value("https://avatars.githubusercontent.com/u/987654"));
	}

	@Test
	void searchesSynchronizedRepositoriesByOwnerAndName() throws Exception {
		GithubUser user = githubUserRepository.save(
				new GithubUser(345678L, "search-owner", "https://avatars.githubusercontent.com/u/345678")
		);
		GithubRepository repository = githubRepositoryRepository.save(new GithubRepository(
				new GithubRepositoryPayload(
						876543L,
						"GitDdo-Backend",
						"Search-Owner/GitDdo-Backend",
						"GitHub portfolio backend",
						"https://github.com/Search-Owner/GitDdo-Backend",
						"Java",
						false,
						false,
						"main",
						10,
						2,
						Instant.parse("2026-08-08T01:00:00Z"),
						Instant.parse("2026-08-08T02:00:00Z")
				)
		));
		userRepositoryAccessRepository.save(new UserRepositoryAccess(user, repository));

		mockMvc.perform(get("/api/v1/github/repositories/search")
						.param("owner", "search")
						.param("name", "backend")
						.with(oauth2Login().attributes(attributes -> {
							attributes.put("id", 345678L);
							attributes.put("login", "search-owner");
						})))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.repositories[0].id").value(876543L))
				.andExpect(jsonPath("$.repositories[0].fullName")
						.value("Search-Owner/GitDdo-Backend"));
	}

	@Test
	void redirectsUnauthenticatedUserToLogin() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void exposesOpenApiDocumentationWithoutLogin() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Gitddo API"))
				.andExpect(jsonPath("$.components.securitySchemes.sessionAuth").exists());
	}

}

package com.gitddo;

import com.gitddo.member.domain.GithubUser;
import com.gitddo.member.domain.GithubUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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

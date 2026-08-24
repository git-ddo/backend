package com.gitddo.analysis.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSnippetPolicyTests {

	private final CodeSnippetPolicy policy = new CodeSnippetPolicy();

	@Test
	void extractsBehavioralMethodInsteadOfClassAndGetters() {
		String file = """
				package com.gitddo.auth;
				
				import java.util.Set;
				
				@Service
				public class AuthFilter {
					public boolean matches(String path) {
						return path.startsWith("/api");
					}
				}
				
				record Unused(String name) {
				}
				""";

		CodeSnippetPolicy.Snippet snippet = policy.extract(file);

		assertThat(snippet).isNotNull();
		assertThat(snippet.startLine()).isEqualTo(7);
		assertThat(snippet.text()).contains("public boolean matches(String path)");
		assertThat(snippet.text()).doesNotContain("package com.gitddo.auth");
		assertThat(snippet.truncated()).isTrue();
	}

	@Test
	void keepsNamedGetMethodWhenItHasParameters() {
		String file = """
				@Service
				public class SejongAuthService {
					public String getSsoToken(SejongLoginRequestDto request) {
						return http.post(request);
					}
				
					private MultiValueMap<String, String> buildFormData(SejongLoginRequestDto request) {
						return new LinkedMultiValueMap<>();
					}
				}
				""";

		CodeSnippetPolicy.Snippet snippet = policy.extract(file);

		assertThat(snippet).isNotNull();
		assertThat(snippet.startLine()).isEqualTo(3);
		assertThat(snippet.text()).contains("public String getSsoToken(SejongLoginRequestDto request)");
	}

	@Test
	void skipsJavaBeanAccessors() {
		String file = """
				public class User {
					private String email;
				
					public String getEmail() {
						return email;
					}
				
					public void setEmail(String email) {
						this.email = email;
					}
				
					public void register() {
						this.email = email.toLowerCase();
					}
				}
				""";

		CodeSnippetPolicy.Snippet snippet = policy.extract(file);

		assertThat(snippet).isNotNull();
		assertThat(snippet.text()).contains("public void register()");
		assertThat(snippet.text()).doesNotContain("getEmail");
	}

	@Test
	void returnsNullWhenFileHasOnlyAccessors() {
		String file = """
				public class User {
					private String email;
				
					public String getEmail() {
						return email;
					}
				
					public void setEmail(String email) {
						this.email = email;
					}
				}
				""";

		assertThat(policy.extract(file)).isNull();
	}

	@Test
	void skipsSecretPathsAndPrivateKeys() {
		assertThat(policy.isSecretPath(".env")).isTrue();
		assertThat(policy.isSecretPath("src/main/resources/credentials.json")).isTrue();
		assertThat(policy.isSecretPath("src/AuthFilter.java")).isFalse();
		assertThat(policy.looksLikeSecret("-----BEGIN PRIVATE KEY-----\nabc")).isTrue();
	}
}

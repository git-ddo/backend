package com.gitddo.analysis.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSnippetPolicyTests {

	private final CodeSnippetPolicy policy = new CodeSnippetPolicy();

	@Test
	void extractsClassBlockInsteadOfWholeFile() {
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
		assertThat(snippet.startLine()).isEqualTo(5);
		assertThat(snippet.text()).startsWith("@Service");
		assertThat(snippet.text()).contains("public class AuthFilter");
		assertThat(snippet.text()).doesNotContain("package com.gitddo.auth");
		assertThat(snippet.truncated()).isTrue();
	}

	@Test
	void skipsSecretPathsAndPrivateKeys() {
		assertThat(policy.isSecretPath(".env")).isTrue();
		assertThat(policy.isSecretPath("src/main/resources/credentials.json")).isTrue();
		assertThat(policy.isSecretPath("src/AuthFilter.java")).isFalse();
		assertThat(policy.looksLikeSecret("-----BEGIN PRIVATE KEY-----\nabc")).isTrue();
	}
}

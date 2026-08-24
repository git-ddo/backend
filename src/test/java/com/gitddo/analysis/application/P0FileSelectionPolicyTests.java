package com.gitddo.analysis.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class P0FileSelectionPolicyTests {

	private final P0FileSelectionPolicy policy = new P0FileSelectionPolicy();

	@Test
	void omitsOsEditorAndLocalEnvPathsFromTree() {
		assertThat(policy.omitFromTree(".DS_Store")).isTrue();
		assertThat(policy.omitFromTree("src/.DS_Store")).isTrue();
		assertThat(policy.omitFromTree(".idea/workspace.xml")).isTrue();
		assertThat(policy.omitFromTree(".env")).isTrue();
		assertThat(policy.omitFromTree(".env/local")).isTrue();
		assertThat(policy.omitFromTree(".env.local")).isTrue();
		assertThat(policy.omitFromTree("Users/kimjunghyun/.zshrc")).isTrue();
		assertThat(policy.omitFromTree("file.dir")).isTrue();
	}

	@Test
	void keepsSourceAndEnvExamplesInTree() {
		assertThat(policy.omitFromTree("src/main/java/com/qeat/service/TableService.java")).isFalse();
		assertThat(policy.omitFromTree(".env.example")).isFalse();
		assertThat(policy.omitFromTree("src/main/java/com/users/User.java")).isFalse();
		assertThat(policy.omitFromTree("package-lock.json")).isFalse();
	}
}

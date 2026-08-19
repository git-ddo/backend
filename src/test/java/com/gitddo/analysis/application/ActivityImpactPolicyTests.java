package com.gitddo.analysis.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityImpactPolicyTests {

	private final ActivityImpactPolicy policy = new ActivityImpactPolicy();

	@Test
	void ignoresLockfileInflationAndPrefersSourceChanges() {
		double lockfile = policy.score("chore: bump deps", List.of(
				new ActivityImpactPolicy.FileChange("package-lock.json", 8_000, 8_000)
		));
		double source = policy.score("fix login", List.of(
				new ActivityImpactPolicy.FileChange("src/main/java/com/gitddo/auth/LoginService.java", 40, 5)
		));

		assertThat(lockfile).isZero();
		assertThat(source).isGreaterThan(lockfile);
	}

	@Test
	void weightsCoreDirectoriesAndArchitectureKeywords() {
		double docs = policy.score("docs: update readme", List.of(
				new ActivityImpactPolicy.FileChange("README.md", 200, 20)
		));
		double domain = policy.score("feat: split billing domain", List.of(
				new ActivityImpactPolicy.FileChange(
						"src/main/java/com/gitddo/billing/domain/Invoice.java",
						40,
						10
				)
		));

		assertThat(domain).isGreaterThan(docs);
		assertThat(domain).isGreaterThan(
				policy.score("fix typo", List.of(
						new ActivityImpactPolicy.FileChange(
								"src/main/java/com/gitddo/billing/domain/Invoice.java",
								40,
								10
						)
				))
		);
	}
}

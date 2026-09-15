package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvidenceKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class P0TechnologyDetectorTests {

	private final P0TechnologyDetector detector = new P0TechnologyDetector();

	@Test
	void detectsSpringStackFromGradleManifest() {
		String gradle = """
				plugins {
				  id 'java'
				  id 'org.springframework.boot' version '3.4.0'
				}
				dependencies {
				  implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
				  implementation 'org.springframework.boot:spring-boot-starter-data-redis'
				  runtimeOnly 'com.mysql:mysql-connector-j'
				}
				""";
		assertThat(detector.detect(EvidenceKind.BUILD_MANIFEST, "build.gradle", gradle))
				.contains(
						"Gradle",
						"Java",
						"Spring Boot",
						"Spring Data JPA",
						"Redis",
						"MySQL"
				)
				.doesNotContain("Docker");
	}

	@Test
	void detectsRedisFromComposeFile() {
		String compose = """
				services:
				  redis:
				    image: redis:7
				""";
		assertThat(detector.detect(EvidenceKind.CONTAINER_CONFIGURATION, "compose.yml", compose))
				.contains("Redis", "Docker")
				.doesNotContain("Spring Boot");
	}

	@Test
	void ignoresReadme() {
		assertThat(detector.detect(
				EvidenceKind.README,
				"README.md",
				"This project uses Spring Boot, MySQL and Redis."
		)).isEmpty();
	}
}

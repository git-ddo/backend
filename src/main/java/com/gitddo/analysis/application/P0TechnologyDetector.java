package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvidenceKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class P0TechnologyDetector {

	private static final int MAX_TECHNOLOGIES_PER_SOURCE = 20;

	private static final List<Rule> RULES = List.of(
			build("Spring Boot", "org.springframework.boot", "spring-boot-starter", "id 'org.springframework.boot'", "id(\"org.springframework.boot\")"),
			build("Spring Data JPA", "spring-boot-starter-data-jpa", "spring-data-jpa"),
			build("Spring Security", "spring-boot-starter-security", "spring-security-"),
			build("Spring Web", "spring-boot-starter-web", "spring-web"),
			both("MySQL", "mysql-connector", "mysql:mysql", "image: mysql", "from mysql"),
			both("PostgreSQL", "postgresql", "org.postgresql", "image: postgres", "from postgres"),
			both("MariaDB", "mariadb"),
			both("Redis", "spring-boot-starter-data-redis", "lettuce-core", "jedis", "image: redis", "from redis"),
			both("MongoDB", "mongodb", "spring-boot-starter-data-mongodb", "from mongo"),
			build("H2", "com.h2database", "h2database"),
			build("Apache Kafka", "spring-kafka", "kafka-clients"),
			both("RabbitMQ", "spring-boot-starter-amqp", "amqp-client", "from rabbitmq"),
			build("Flyway", "flyway-core", "flyway-database", "org.flywaydb"),
			build("Liquibase", "liquibase-core"),
			build("MyBatis", "mybatis"),
			build("QueryDSL", "querydsl"),
			build("Lombok", "lombok"),
			build("JUnit", "junit-jupiter", "org.junit.jupiter"),
			build("Testcontainers", "org.testcontainers", "testcontainers"),
			build("OpenAPI", "springdoc-openapi", "swagger"),
			build("JWT", "jjwt", "java-jwt"),
			build("Gradle", "id 'java'", "id(\"java\")", "plugins {"),
			build("Maven", "<artifactid>", "maven.compiler"),
			build("Node.js", "\"dependencies\"", "\"devdependencies\""),
			build("React", "\"react\""),
			build("Next.js", "\"next\""),
			build("Vue", "\"vue\""),
			build("NestJS", "\"@nestjs/"),
			build("Express", "\"express\""),
			both("Java", "eclipse-temurin", "openjdk", "amazoncorretto", "id 'java'", "id(\"java\")", "<maven.compiler"),
			both("Python", "from python", "django", "fastapi", "flask"),
			build("Go", "module "),
			container("Nginx", "from nginx", "image: nginx"),
			container("Docker", "from ", "services:")
	);

	public List<String> detect(EvidenceKind kind, String path, String content) {
		if (!isSourceKind(kind) || content == null || content.isBlank()) {
			return List.of();
		}
		String haystack = ((path == null ? "" : path) + "\n" + content).toLowerCase(Locale.ROOT);
		Set<String> detected = new LinkedHashSet<>();
		if (kind == EvidenceKind.BUILD_MANIFEST) {
			addToolFromPath(path, detected);
		}
		for (Rule rule : RULES) {
			if (!rule.appliesTo(kind) || !rule.matches(haystack)) {
				continue;
			}
			detected.add(rule.name());
			if (detected.size() >= MAX_TECHNOLOGIES_PER_SOURCE) {
				break;
			}
		}
		return List.copyOf(detected);
	}

	boolean isSourceKind(EvidenceKind kind) {
		return kind == EvidenceKind.BUILD_MANIFEST
				|| kind == EvidenceKind.CONTAINER_CONFIGURATION;
	}

	private void addToolFromPath(String path, Set<String> detected) {
		if (path == null) {
			return;
		}
		String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		if (fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts")) {
			detected.add("Gradle");
		}
		if (fileName.equals("pom.xml")) {
			detected.add("Maven");
		}
		if (fileName.equals("package.json")) {
			detected.add("Node.js");
		}
		if (fileName.equals("requirements.txt") || fileName.equals("pyproject.toml")) {
			detected.add("Python");
		}
		if (fileName.equals("go.mod")) {
			detected.add("Go");
		}
		if (fileName.equals("cargo.toml")) {
			detected.add("Rust");
		}
	}

	private static Rule build(String name, String... needles) {
		return rule(EnumSet.of(EvidenceKind.BUILD_MANIFEST), name, needles);
	}

	private static Rule container(String name, String... needles) {
		return rule(EnumSet.of(EvidenceKind.CONTAINER_CONFIGURATION), name, needles);
	}

	private static Rule both(String name, String... needles) {
		return rule(
				EnumSet.of(EvidenceKind.BUILD_MANIFEST, EvidenceKind.CONTAINER_CONFIGURATION),
				name,
				needles
		);
	}

	private static Rule rule(Set<EvidenceKind> kinds, String name, String... needles) {
		List<Pattern> patterns = new ArrayList<>();
		for (String needle : needles) {
			patterns.add(Pattern.compile(Pattern.quote(needle.toLowerCase(Locale.ROOT))));
		}
		return new Rule(name, Set.copyOf(kinds), List.copyOf(patterns));
	}

	private record Rule(String name, Set<EvidenceKind> kinds, List<Pattern> patterns) {

		private boolean appliesTo(EvidenceKind kind) {
			return kinds.contains(kind);
		}

		private boolean matches(String haystack) {
			return patterns.stream().anyMatch(pattern -> pattern.matcher(haystack).find());
		}
	}
}

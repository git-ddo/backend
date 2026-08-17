package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.P0EvidenceKind;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class P0FileSelectionPolicy {

	static final int MAX_SELECTED_FILES = 10;
	static final int MAX_TREE_ENTRIES = 10_000;
	static final int MAX_FILE_BYTES = 20_000;
	static final int MAX_README_BYTES = 30_000;

	private static final Set<String> BUILD_MANIFESTS = Set.of(
			"build.gradle",
			"build.gradle.kts",
			"settings.gradle",
			"settings.gradle.kts",
			"pom.xml",
			"package.json",
			"requirements.txt",
			"pyproject.toml",
			"go.mod",
			"cargo.toml"
	);

	public Optional<P0EvidenceKind> classify(String path) {
		String normalized = path.toLowerCase(Locale.ROOT);
		String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);

		if (isExcluded(normalized)) {
			return Optional.empty();
		}
		if (!normalized.contains("/") && fileName.startsWith("readme")) {
			return Optional.of(P0EvidenceKind.README);
		}
		if (BUILD_MANIFESTS.contains(fileName)) {
			return Optional.of(P0EvidenceKind.BUILD_MANIFEST);
		}
		if (normalized.startsWith(".github/workflows/")
				&& (normalized.endsWith(".yml") || normalized.endsWith(".yaml"))) {
			return Optional.of(P0EvidenceKind.CI_CONFIGURATION);
		}
		if (fileName.equals("dockerfile")
				|| fileName.startsWith("docker-compose")
				|| fileName.startsWith("compose.")) {
			return Optional.of(P0EvidenceKind.CONTAINER_CONFIGURATION);
		}
		if ((normalized.contains("openapi") || normalized.contains("swagger"))
				&& (normalized.endsWith(".yml")
						|| normalized.endsWith(".yaml")
						|| normalized.endsWith(".json"))) {
			return Optional.of(P0EvidenceKind.API_DOCUMENTATION);
		}
		return Optional.empty();
	}

	public int maxBytes(P0EvidenceKind kind) {
		return kind == P0EvidenceKind.README
				? MAX_README_BYTES
				: MAX_FILE_BYTES;
	}

	private boolean isExcluded(String path) {
		return path.equals(".env")
				|| path.startsWith(".env.")
				|| path.endsWith(".pem")
				|| path.endsWith(".key")
				|| path.contains("credentials")
				|| path.contains("/node_modules/")
				|| path.startsWith("node_modules/")
				|| path.contains("/vendor/")
				|| path.startsWith("vendor/")
				|| path.contains("/build/")
				|| path.startsWith("build/")
				|| path.contains("/dist/")
				|| path.startsWith("dist/")
				|| path.contains("/target/")
				|| path.startsWith("target/")
				|| path.endsWith(".lock");
	}
}

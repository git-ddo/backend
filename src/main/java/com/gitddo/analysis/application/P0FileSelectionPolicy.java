package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvidenceKind;
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

	public Optional<EvidenceKind> classify(String path) {
		String normalized = path.toLowerCase(Locale.ROOT);
		String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);

		if (isExcluded(normalized)) {
			return Optional.empty();
		}
		if (!normalized.contains("/") && fileName.startsWith("readme")) {
			return Optional.of(EvidenceKind.README);
		}
		if (BUILD_MANIFESTS.contains(fileName)) {
			return Optional.of(EvidenceKind.BUILD_MANIFEST);
		}
		if (normalized.startsWith(".github/workflows/")
				&& (normalized.endsWith(".yml") || normalized.endsWith(".yaml"))) {
			return Optional.of(EvidenceKind.CI_CONFIGURATION);
		}
		if (fileName.equals("dockerfile")
				|| fileName.startsWith("docker-compose")
				|| fileName.startsWith("compose.")) {
			return Optional.of(EvidenceKind.CONTAINER_CONFIGURATION);
		}
		if ((normalized.contains("openapi") || normalized.contains("swagger"))
				&& (normalized.endsWith(".yml")
						|| normalized.endsWith(".yaml")
						|| normalized.endsWith(".json"))) {
			return Optional.of(EvidenceKind.API_DOCUMENTATION);
		}
		return Optional.empty();
	}

	public int maxBytes(EvidenceKind kind) {
		return kind == EvidenceKind.README
				? MAX_README_BYTES
				: MAX_FILE_BYTES;
	}

	public boolean omitFromTree(String path) {
		PathParts parts = parts(path);
		return isSecretPath(parts)
				|| isDependencyOrBuildOutput(parts.normalized())
				|| isEditorOrOsJunk(parts)
				|| isLocalFilesystemPath(parts.normalized());
	}

	public boolean isEnvSecretPath(String path) {
		return isEnvSecret(parts(path));
	}

	public boolean isEditorOrOsJunk(String path) {
		return isEditorOrOsJunk(parts(path));
	}

	private boolean isExcluded(String path) {
		PathParts parts = new PathParts(path, fileName(path));
		return isSecretPath(parts)
				|| isDependencyOrBuildOutput(path)
				|| path.endsWith(".lock");
	}

	private boolean isSecretPath(PathParts parts) {
		String path = parts.normalized();
		String fileName = parts.fileName();
		return isEnvSecret(parts)
				|| fileName.endsWith(".pem")
				|| fileName.endsWith(".key")
				|| path.contains("credentials");
	}

	private boolean isEnvSecret(PathParts parts) {
		String path = parts.normalized();
		String fileName = parts.fileName();
		if (fileName.equals(".env") || fileName.equals(".envrc") || fileName.equals(".env.local")) {
			return true;
		}
		if (path.equals(".env") || path.startsWith(".env/") || path.contains("/.env/")) {
			return true;
		}
		if (!fileName.startsWith(".env.")) {
			return false;
		}
		return !fileName.endsWith(".example")
				&& !fileName.endsWith(".sample")
				&& !fileName.endsWith(".template");
	}

	private boolean isDependencyOrBuildOutput(String path) {
		return path.contains("/node_modules/")
				|| path.startsWith("node_modules/")
				|| path.contains("/vendor/")
				|| path.startsWith("vendor/")
				|| path.contains("/build/")
				|| path.startsWith("build/")
				|| path.contains("/dist/")
				|| path.startsWith("dist/")
				|| path.contains("/target/")
				|| path.startsWith("target/");
	}

	private boolean isEditorOrOsJunk(PathParts parts) {
		String path = parts.normalized();
		String fileName = parts.fileName();
		return fileName.equals(".ds_store")
				|| fileName.equals("thumbs.db")
				|| fileName.equals("desktop.ini")
				|| fileName.equals("file.dir")
				|| fileName.startsWith("._")
				|| fileName.endsWith(".iml")
				|| path.equals(".idea")
				|| path.startsWith(".idea/")
				|| path.contains("/.idea/")
				|| path.equals(".vscode")
				|| path.startsWith(".vscode/")
				|| path.contains("/.vscode/")
				|| path.contains("/__pycache__/")
				|| path.startsWith("__pycache__/")
				|| path.equals(".gradle")
				|| path.startsWith(".gradle/")
				|| path.contains("/.gradle/");
	}

	private boolean isLocalFilesystemPath(String path) {
		return path.startsWith("users/")
				|| path.startsWith("home/")
				|| path.matches("^[a-z]:/.*");
	}

	private PathParts parts(String path) {
		String normalized = path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
		if (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		return new PathParts(normalized, fileName(normalized));
	}

	private String fileName(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	private record PathParts(String normalized, String fileName) {
	}
}

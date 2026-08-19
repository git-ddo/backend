package com.gitddo.analysis.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ActivityImpactPolicy {

	private static final Set<String> LOCK_FILE_NAMES = Set.of(
			"package-lock.json",
			"yarn.lock",
			"pnpm-lock.yaml",
			"npm-shrinkwrap.json",
			"bun.lock",
			"bun.lockb",
			"cargo.lock",
			"composer.lock",
			"poetry.lock",
			"go.sum",
			"gemfile.lock"
	);
	private static final Set<String> SOURCE_EXTENSIONS = Set.of(
			".java",
			".kt",
			".kts",
			".scala",
			".groovy",
			".go",
			".py",
			".ts",
			".tsx",
			".js",
			".jsx",
			".cs",
			".rb",
			".swift",
			".rs"
	);
	private static final List<String> CORE_PATH_SEGMENTS = List.of(
			"/domain/",
			"/entity/",
			"/service/",
			"/application/",
			"/usecase/",
			"/controller/",
			"/presentation/",
			"/adapter/"
	);
	private static final Pattern KEYWORD = Pattern.compile(
			"(?i)(^|[\\s:()\\-_/])(feat|feature|refactor|arch|architecture|domain)($|[\\s:()\\-_/])"
	);

	public double score(String message, List<FileChange> files) {
		double weightedLines = 0;
		for (FileChange file : files) {
			int lines = Math.max(0, file.additions()) + Math.max(0, file.deletions());
			if (lines == 0) {
				continue;
			}
			weightedLines += lines * pathWeight(file.path());
		}
		if (weightedLines <= 0) {
			return 0;
		}
		if (KEYWORD.matcher(nullToEmpty(message)).find()) {
			return weightedLines * 1.4;
		}
		return weightedLines;
	}

	double pathWeight(String path) {
		String normalized = normalize(path);
		if (normalized.isEmpty() || isNoise(normalized)) {
			return 0;
		}
		if (isSource(normalized)) {
			if (isCorePath(normalized)) {
				return 3.0;
			}
			if (isTestPath(normalized)) {
				return 0.8;
			}
			return 1.5;
		}
		if (normalized.endsWith(".md")
				|| normalized.endsWith(".yml")
				|| normalized.endsWith(".yaml")
				|| normalized.endsWith(".properties")) {
			return 0.15;
		}
		return 0.2;
	}

	private boolean isNoise(String path) {
		String fileName = fileName(path);
		if (LOCK_FILE_NAMES.contains(fileName)) {
			return true;
		}
		return fileName.endsWith(".min.js")
				|| fileName.endsWith(".min.css")
				|| fileName.endsWith(".map")
				|| path.contains("/node_modules/")
				|| path.contains("/vendor/")
				|| path.contains("/generated/")
				|| path.contains("/target/")
				|| path.contains("/dist/");
	}

	private boolean isSource(String path) {
		String fileName = fileName(path);
		int dot = fileName.lastIndexOf('.');
		if (dot < 0) {
			return false;
		}
		return SOURCE_EXTENSIONS.contains(fileName.substring(dot));
	}

	private boolean isCorePath(String path) {
		return CORE_PATH_SEGMENTS.stream().anyMatch(path::contains);
	}

	private boolean isTestPath(String path) {
		return path.contains("/test/")
				|| path.contains("/tests/")
				|| path.startsWith("src/test/");
	}

	private String fileName(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	private String normalize(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.startsWith("/") ? normalized : "/" + normalized;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	public record FileChange(
			String path,
			int additions,
			int deletions
	) {
	}
}

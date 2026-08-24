package com.gitddo.analysis.application;

import java.util.List;
import java.util.Locale;

final class CurrentPathResolver {

	private static final List<String> ROLE_SEGMENTS = List.of(
			"domain",
			"entity",
			"service",
			"application",
			"usecase",
			"controller",
			"presentation",
			"adapter"
	);

	ResolvedPath resolve(String historicalPath, CurrentTreeIndex tree) {
		if (historicalPath == null || historicalPath.isBlank() || tree.isEmpty()) {
			return ResolvedPath.unresolved(historicalPath);
		}
		String exact = tree.exactOrNormalized(historicalPath);
		if (exact != null) {
			return ResolvedPath.resolved(historicalPath, exact);
		}
		String roleAndFile = roleAndFileKey(historicalPath);
		if (roleAndFile != null) {
			List<String> roleMatches = tree.pathsWithRoleAndFile(roleAndFile);
			if (roleMatches.size() == 1) {
				return ResolvedPath.resolved(historicalPath, roleMatches.getFirst());
			}
		}
		List<String> nameMatches = tree.pathsWithFileName(fileName(historicalPath));
		if (nameMatches.size() == 1) {
			return ResolvedPath.resolved(historicalPath, nameMatches.getFirst());
		}
		return ResolvedPath.unresolved(historicalPath);
	}

	static String roleAndFileKey(String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		String normalized = normalize(path);
		String fileName = fileName(normalized);
		if (fileName.isEmpty()) {
			return null;
		}
		for (String role : ROLE_SEGMENTS) {
			String marker = "/" + role + "/";
			int index = normalized.lastIndexOf(marker);
			if (index >= 0) {
				return role + "/" + fileName;
			}
		}
		return null;
	}

	private static String fileName(String path) {
		String normalized = normalize(path);
		int slash = normalized.lastIndexOf('/');
		return slash < 0 ? normalized : normalized.substring(slash + 1);
	}

	private static String normalize(String path) {
		String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.startsWith("/") ? normalized.substring(1) : normalized;
	}

	record ResolvedPath(String originalPath, String currentPath, boolean found) {

		static ResolvedPath unresolved(String originalPath) {
			return new ResolvedPath(originalPath, null, false);
		}

		static ResolvedPath resolved(String originalPath, String currentPath) {
			return new ResolvedPath(originalPath, currentPath, true);
		}

		boolean remapped() {
			return found && originalPath != null && currentPath != null
					&& !normalize(originalPath).equals(normalize(currentPath));
		}
	}
}

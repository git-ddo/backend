package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CurrentTreeIndex {

	private final Map<String, String> byNormalizedPath = new LinkedHashMap<>();
	private final Map<String, List<String>> byFileName = new LinkedHashMap<>();
	private final Map<String, List<String>> byRoleAndFile = new LinkedHashMap<>();

	private CurrentTreeIndex() {
	}

	static CurrentTreeIndex from(List<P0EvidenceSnapshot.Evidence> evidence, String repositoryId) {
		CurrentTreeIndex index = new CurrentTreeIndex();
		for (P0EvidenceSnapshot.Evidence item : evidence) {
			if (!repositoryId.equals(item.repositoryId()) || item.kind() != EvidenceKind.FILE_TREE_SUMMARY) {
				continue;
			}
			if (item.content() == null || item.content().isBlank()) {
				continue;
			}
			for (String line : item.content().lines().toList()) {
				if (!line.startsWith("blob\t")) {
					continue;
				}
				String path = line.substring("blob\t".length()).strip();
				if (!path.isEmpty()) {
					index.add(path);
				}
			}
		}
		return index;
	}

	boolean isEmpty() {
		return byNormalizedPath.isEmpty();
	}

	String exactOrNormalized(String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		return byNormalizedPath.get(normalize(path));
	}

	List<String> pathsWithRoleAndFile(String roleAndFile) {
		if (roleAndFile == null) {
			return List.of();
		}
		return List.copyOf(byRoleAndFile.getOrDefault(roleAndFile, List.of()));
	}

	List<String> pathsWithFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return List.of();
		}
		return List.copyOf(byFileName.getOrDefault(fileName.toLowerCase(Locale.ROOT), List.of()));
	}

	private void add(String path) {
		String normalized = normalize(path);
		byNormalizedPath.putIfAbsent(normalized, path);
		String fileName = fileName(normalized);
		byFileName.computeIfAbsent(fileName, ignored -> new ArrayList<>()).add(path);
		String roleAndFile = CurrentPathResolver.roleAndFileKey(path);
		if (roleAndFile != null) {
			byRoleAndFile.computeIfAbsent(roleAndFile, ignored -> new ArrayList<>()).add(path);
		}
	}

	private static String fileName(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	private static String normalize(String path) {
		String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.startsWith("/") ? normalized.substring(1) : normalized;
	}
}

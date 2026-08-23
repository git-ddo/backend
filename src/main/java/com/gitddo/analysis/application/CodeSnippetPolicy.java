package com.gitddo.analysis.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CodeSnippetPolicy {

	static final int MAX_SNIPPETS_PER_REPOSITORY = 8;
	static final int MAX_FILE_CANDIDATES = 16;
	static final int MAX_FILE_BYTES = 80_000;
	static final int MAX_SNIPPET_LINES = 40;
	static final int MAX_SNIPPET_CHARS = 4_000;

	private static final Pattern INTERESTING = Pattern.compile(
			"(?i)^\\s*(?:@\\w+|"
					+ "(?:public |protected |private )?(?:static )?(?:final )?(?:class|interface|enum|record)\\s|"
					+ "(?:export )?(?:async )?(?:function|class|const|def )\\s|"
					+ "func\\s)"
	);
	private static final Pattern IMPORT_OR_PACKAGE = Pattern.compile(
			"(?i)^\\s*(package |import |from |using |#include )"
	);
	private static final Pattern SECRET_MARKER = Pattern.compile(
			"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|AWS_SECRET_ACCESS_KEY"
	);

	public boolean isSecretPath(String path) {
		String normalized = normalize(path);
		String fileName = fileName(normalized);
		return fileName.equals(".env")
				|| fileName.startsWith(".env.")
				|| fileName.endsWith(".pem")
				|| fileName.endsWith(".key")
				|| normalized.contains("credentials")
				|| normalized.contains("/secrets/");
	}

	public boolean looksLikeSecret(String content) {
		return content != null && SECRET_MARKER.matcher(content).find();
	}

	public Snippet extract(String fileContent) {
		List<String> lines = splitLines(fileContent);
		if (lines.isEmpty()) {
			return null;
		}
		int startIndex = firstInterestingIndex(lines);
		if (startIndex < 0) {
			startIndex = firstContentAfterImports(lines);
		}
		startIndex = includeLeadingAnnotations(lines, startIndex);
		int endIndex = Math.min(lines.size(), startIndex + MAX_SNIPPET_LINES);
		List<String> selected = new ArrayList<>(lines.subList(startIndex, endIndex));
		while (joinedLength(selected) > MAX_SNIPPET_CHARS && selected.size() > 1) {
			selected.removeLast();
			endIndex = startIndex + selected.size();
		}
		String text = String.join("\n", selected);
		if (text.isBlank()) {
			return null;
		}
		boolean truncated = startIndex > 0 || endIndex < lines.size() || fileContent.length() > text.length();
		return new Snippet(text, startIndex + 1, startIndex + selected.size(), truncated);
	}

	private int firstInterestingIndex(List<String> lines) {
		for (int index = 0; index < lines.size(); index++) {
			if (INTERESTING.matcher(lines.get(index)).find()) {
				return index;
			}
		}
		return -1;
	}

	private int firstContentAfterImports(List<String> lines) {
		int index = 0;
		while (index < lines.size()
				&& (lines.get(index).isBlank() || IMPORT_OR_PACKAGE.matcher(lines.get(index)).find())) {
			index++;
		}
		return Math.min(index, lines.size() - 1);
	}

	private int includeLeadingAnnotations(List<String> lines, int startIndex) {
		int index = startIndex;
		while (index > 0 && lines.get(index - 1).strip().startsWith("@")) {
			index--;
			if (startIndex - index >= 5) {
				break;
			}
		}
		return index;
	}

	private int joinedLength(List<String> lines) {
		int length = 0;
		for (int index = 0; index < lines.size(); index++) {
			length += lines.get(index).length();
			if (index > 0) {
				length++;
			}
		}
		return length;
	}

	private List<String> splitLines(String content) {
		if (content == null || content.isEmpty()) {
			return List.of();
		}
		return content.replace("\r\n", "\n").replace('\r', '\n').lines().toList();
	}

	private String normalize(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.startsWith("/") ? normalized : "/" + normalized;
	}

	private String fileName(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	public record Snippet(
			String text,
			int startLine,
			int endLine,
			boolean truncated
	) {
	}
}

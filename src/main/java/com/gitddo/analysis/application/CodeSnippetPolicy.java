package com.gitddo.analysis.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodeSnippetPolicy {

	static final int MAX_SNIPPETS_PER_REPOSITORY = 8;
	static final int MAX_SNIPPETS_PER_SOURCE = 2;
	static final int MAX_FILE_CANDIDATES = 16;
	static final int MAX_FILE_BYTES = 80_000;
	static final int MAX_SNIPPET_LINES = 40;
	static final int MAX_SNIPPET_CHARS = 4_000;

	private static final Pattern SECRET_MARKER = Pattern.compile(
			"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|AWS_SECRET_ACCESS_KEY"
	);
	private static final Pattern METHOD = Pattern.compile(
			"(?i)(?:(?:public|protected|private|static|final|synchronized|native|default|abstract)\\s+)+"
					+ "[\\w.<>,?\\[\\]]+[\\w.<>,?\\[\\]\\s]*\\s+([\\w$]+)\\s*\\(([^)]*)\\)"
	);
	private static final Pattern ACCESSOR_NAME = Pattern.compile("^(get|set|is)[A-Z].*");
	private static final Set<String> NOT_METHOD_NAMES = Set.of(
			"if",
			"for",
			"while",
			"switch",
			"catch",
			"synchronized",
			"return",
			"new",
			"throw",
			"assert"
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
		int startIndex = firstNonAccessorMethodIndex(lines);
		if (startIndex < 0) {
			return null;
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

	private int firstNonAccessorMethodIndex(List<String> lines) {
		for (int index = 0; index < lines.size(); index++) {
			MethodSignature method = methodSignature(lines.get(index));
			if (method != null && !isJavaBeanAccessor(method)) {
				return index;
			}
		}
		return -1;
	}

	private MethodSignature methodSignature(String line) {
		Matcher matcher = METHOD.matcher(line);
		if (!matcher.find()) {
			return null;
		}
		String name = matcher.group(1);
		if (name == null || NOT_METHOD_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
			return null;
		}
		return new MethodSignature(name, matcher.group(2) == null ? "" : matcher.group(2).strip());
	}

	private boolean isJavaBeanAccessor(MethodSignature method) {
		if (!ACCESSOR_NAME.matcher(method.name()).matches()) {
			return false;
		}
		String params = method.params();
		if (method.name().startsWith("set")) {
			return !params.isEmpty() && !params.contains(",");
		}
		return params.isEmpty();
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

	private record MethodSignature(String name, String params) {
	}
}

package com.gitddo.analysis.client;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.FindingCategory;
import com.gitddo.analysis.contract.FindingSeverity;
import com.gitddo.analysis.contract.LimitationCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "gitddo.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockPortfolioReportClient implements PortfolioReportClient {

	static final String EVALUATOR_VERSION = "mock-1.0";

	@Override
	public AiAnalysisResponse requestReport(AiAnalysisRequest request) {
		Set<AnalysisDepth> completed = completedLevels(request);
		List<AnalysisDepth> usedLevels = usableLevels(request.requestedAnalysisDepth(), completed);
		List<AiAnalysisResponse.Limitation> limitations = limitations(request.requestedAnalysisDepth(), usedLevels);
		List<AiAnalysisResponse.RepositoryReport> repositories = new ArrayList<>();
		int findingSequence = 1;
		List<AiAnalysisResponse.CoachingItem> strengths = new ArrayList<>();
		List<AiAnalysisResponse.CoachingItem> gaps = new ArrayList<>();
		for (AiAnalysisRequest.Repository repository : request.repositories()) {
			List<AiAnalysisResponse.Finding> findings = new ArrayList<>();
			for (AiAnalysisRequest.Evidence evidence : repository.evidence()) {
				FindingCategory category = category(evidence.factKey());
				if (category == null || !usedLevels.contains(AnalysisDepth.P0)) {
					continue;
				}
				String findingId = "find_%03d".formatted(findingSequence++);
				boolean present = evidence.value() != null && !evidence.value().isBlank();
				findings.add(new AiAnalysisResponse.Finding(
						findingId,
						category,
						present ? FindingSeverity.POSITIVE : FindingSeverity.GAP,
						title(category, evidence.factKey(), present),
						detail(category, evidence, present),
						List.of(evidence.evidenceId()),
						List.of()
				));
				AiAnalysisResponse.CoachingItem item = new AiAnalysisResponse.CoachingItem(
						coachingText(category, evidence.factKey(), present),
						List.of(evidence.evidenceId())
				);
				if (present) {
					strengths.add(item);
				} else {
					gaps.add(item);
				}
				if (findings.size() >= 6) {
					break;
				}
			}
			repositories.add(new AiAnalysisResponse.RepositoryReport(
					repository.repositoryId(),
					repository.repositoryFullName(),
					repository.snapshotHashAlgorithm(),
					repository.snapshotSha(),
					findings
			));
		}
		if (gaps.isEmpty() && usedLevels.equals(List.of(AnalysisDepth.P0))) {
			gaps.add(new AiAnalysisResponse.CoachingItem(
					"활동 근거와 선별 코드가 없어 기여도와 코드 품질은 판단하지 않았습니다.",
					List.of()
			));
		}
		return new AiAnalysisResponse(
				AiAnalysisResponse.SCHEMA_VERSION,
				request.analysisId(),
				EVALUATOR_VERSION,
				request.requestedAnalysisDepth(),
				usedLevels,
				summary(request, usedLevels),
				repositories,
				new AiAnalysisResponse.Coaching(
						strengths,
						gaps,
						List.of(new AiAnalysisResponse.CoachingItem(
								"다음 평가에서는 커밋·PR 근거(P1)를 추가해 기여 주장을 확인할 수 있습니다.",
								List.of()
						)),
						interviewQuestions(request)
				),
				limitations
		);
	}

	private Set<AnalysisDepth> completedLevels(AiAnalysisRequest request) {
		Set<AnalysisDepth> completed = EnumSet.noneOf(AnalysisDepth.class);
		request.repositories().forEach(repository ->
				completed.addAll(repository.completedEvidenceLevels()));
		return completed;
	}

	private List<AnalysisDepth> usableLevels(
			AnalysisDepth requested,
			Set<AnalysisDepth> completed
	) {
		int maxRank = Math.min(rank(requested), completed.stream()
				.mapToInt(this::rank)
				.max()
				.orElse(-1));
		List<AnalysisDepth> used = new ArrayList<>();
		for (AnalysisDepth depth : AnalysisDepth.values()) {
			if (rank(depth) <= maxRank && completed.contains(depth)) {
				used.add(depth);
			}
		}
		if (used.isEmpty()) {
			used.add(AnalysisDepth.P0);
		}
		return List.copyOf(used);
	}

	private int rank(AnalysisDepth depth) {
		return switch (depth) {
			case P0 -> 0;
			case P1 -> 1;
			case P2 -> 2;
		};
	}

	private List<AiAnalysisResponse.Limitation> limitations(
			AnalysisDepth requested,
			List<AnalysisDepth> used
	) {
		List<AiAnalysisResponse.Limitation> limitations = new ArrayList<>();
		if (!used.contains(AnalysisDepth.P1) || requested == AnalysisDepth.P0) {
			limitations.add(new AiAnalysisResponse.Limitation(
					LimitationCode.P0_ONLY,
					"이번 근거에는 P0만 있어 구조·문서·스택만 해석했습니다. 코드 품질과 기여도는 단정하지 않았습니다."
			));
		}
		if (requested != AnalysisDepth.P0 && !used.contains(AnalysisDepth.P1)) {
			limitations.add(new AiAnalysisResponse.Limitation(
					LimitationCode.MISSING_ACTIVITY_EVIDENCE,
					"커밋·PR 활동 근거가 없어 기여 주장을 확인하지 않았습니다."
			));
		}
		if (rank(requested) >= rank(AnalysisDepth.P2) && !used.contains(AnalysisDepth.P2)) {
			limitations.add(new AiAnalysisResponse.Limitation(
					LimitationCode.MISSING_CODE_EVIDENCE,
					"선별 코드 근거가 없어 코드 품질을 판단하지 않았습니다."
			));
		}
		return limitations;
	}

	private FindingCategory category(String factKey) {
		if (factKey == null) {
			return null;
		}
		return switch (factKey) {
			case "README", "API_DOCUMENTATION" -> FindingCategory.DOCUMENTATION;
			case "BUILD_MANIFEST", "LANGUAGE_BREAKDOWN",
					"CI_CONFIGURATION", "CONTAINER_CONFIGURATION" -> FindingCategory.STACK;
			case "PROJECT_STRUCTURE", "FILE_TREE_SUMMARY", "REPOSITORY_METADATA" -> FindingCategory.STRUCTURE;
			default -> null;
		};
	}

	private String title(FindingCategory category, String factKey, boolean present) {
		String label = switch (category) {
			case DOCUMENTATION -> "문서";
			case STACK -> "스택";
			case STRUCTURE -> "구조";
			default -> factKey;
		};
		return present ? label + " 근거가 있습니다." : label + " 근거가 비어 있습니다.";
	}

	private String detail(
			FindingCategory category,
			AiAnalysisRequest.Evidence evidence,
			boolean present
	) {
		if (!present) {
			return evidence.factKey() + " 값이 비어 있습니다.";
		}
		String preview = evidence.value().strip().lines().findFirst().orElse(evidence.factKey());
		if (preview.length() > 180) {
			preview = preview.substring(0, 180);
		}
		return category.name().toLowerCase(Locale.ROOT) + " 근거: " + preview;
	}

	private String coachingText(FindingCategory category, String factKey, boolean present) {
		if (present) {
			return factKey + "를 바탕으로 " + category.name() + "을 확인할 수 있습니다.";
		}
		return factKey + " 근거가 부족합니다.";
	}

	private String summary(AiAnalysisRequest request, List<AnalysisDepth> usedLevels) {
		return "%s %s 포트폴리오를 %s 근거만으로 해석했습니다. GitHub를 직접 보지 않았고, 전달된 Evidence와 UserClaim만 사용했습니다."
				.formatted(
						request.targetJob(),
						request.targetCareerLevel(),
						usedLevels.stream()
								.map(Enum::name)
								.reduce((left, right) -> left + "+" + right)
								.orElse("P0")
				);
	}

	private List<String> interviewQuestions(AiAnalysisRequest request) {
		String repositoryName = request.repositories().stream()
				.map(AiAnalysisRequest.Repository::repositoryFullName)
				.findFirst()
				.orElse("선택한 저장소");
		return List.of(
				repositoryName + "의 디렉터리 구조와 빌드 도구를 어떻게 설명하시겠어요?",
				"README에 적힌 실행 방법과 실제 개발 환경이 같은지 어떻게 확인하시겠어요?"
		);
	}
}

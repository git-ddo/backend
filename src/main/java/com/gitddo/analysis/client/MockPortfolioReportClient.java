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
			addFindings(
					findings,
					repository.evidence(),
					usedLevels,
					findingSequence,
					5,
					AnalysisDepth.P0
			);
			findingSequence += findings.size();
			int beforeP1 = findings.size();
			addFindings(
					findings,
					repository.evidence(),
					usedLevels,
					findingSequence,
					5,
					AnalysisDepth.P1
			);
			findingSequence += findings.size() - beforeP1;
			if (usedLevels.contains(AnalysisDepth.P1) && !repository.userClaims().isEmpty()) {
				AiAnalysisRequest.UserClaim claim = repository.userClaims().getFirst();
				String activityId = repository.evidence().stream()
						.filter(item -> item.analysisDepth() == AnalysisDepth.P1)
						.map(AiAnalysisRequest.Evidence::evidenceId)
						.findFirst()
						.orElse(null);
				findings.add(new AiAnalysisResponse.Finding(
						"find_%03d".formatted(findingSequence++),
						FindingCategory.CONTRIBUTION,
						FindingSeverity.INFO,
						"사용자 기여 주장과 활동 근거를 대조했습니다.",
						"UserClaim을 활동 Evidence와 함께 해석했으며, 코드 품질은 단정하지 않았습니다.",
						activityId == null ? List.of() : List.of(activityId),
						List.of(claim.claimId())
				));
			}
			for (AiAnalysisResponse.Finding finding : findings) {
				if (finding.evidenceRefs().isEmpty()) {
					continue;
				}
				AiAnalysisResponse.CoachingItem item = new AiAnalysisResponse.CoachingItem(
						finding.title(),
						finding.evidenceRefs()
				);
				if (finding.severity() == FindingSeverity.GAP) {
					gaps.add(item);
				} else {
					strengths.add(item);
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
		List<String> citedEvidence = firstEvidenceIds(request);
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
						nextActions(request, citedEvidence),
						jobAppeal(request, citedEvidence),
						portfolioStatements(request, citedEvidence),
						interviewQuestions(request, usedLevels, citedEvidence)
				),
				limitations
		);
	}

	private void addFindings(
			List<AiAnalysisResponse.Finding> findings,
			List<AiAnalysisRequest.Evidence> evidence,
			List<AnalysisDepth> usedLevels,
			int findingSequence,
			int limit,
			AnalysisDepth depth
	) {
		int added = 0;
		for (AiAnalysisRequest.Evidence item : evidence) {
			if (item.analysisDepth() != depth || added >= limit) {
				continue;
			}
			FindingCategory category = category(item.factKey());
			if (category == null || !usedLevels.contains(requiredDepth(category))) {
				continue;
			}
			boolean present = item.value() != null && !item.value().isBlank();
			findings.add(new AiAnalysisResponse.Finding(
					"find_%03d".formatted(findingSequence + added),
					category,
					present ? FindingSeverity.POSITIVE : FindingSeverity.GAP,
					title(category, item.factKey(), present),
					detail(category, item, present),
					List.of(item.evidenceId()),
					List.of()
			));
			added++;
		}
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

	private AnalysisDepth requiredDepth(FindingCategory category) {
		return switch (category) {
			case STRUCTURE, DOCUMENTATION, STACK -> AnalysisDepth.P0;
			case ACTIVITY, CONTRIBUTION -> AnalysisDepth.P1;
			case CODE_QUALITY -> AnalysisDepth.P2;
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
		if (used.contains(AnalysisDepth.P1) && !used.contains(AnalysisDepth.P2)) {
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
			case "COMMIT_SUMMARY", "PULL_REQUEST", "CHANGED_FILES", "ACTIVITY_SUMMARY" -> FindingCategory.ACTIVITY;
			default -> null;
		};
	}

	private String title(FindingCategory category, String factKey, boolean present) {
		String label = switch (category) {
			case DOCUMENTATION -> "문서";
			case STACK -> "스택";
			case STRUCTURE -> "구조";
			case ACTIVITY, CONTRIBUTION -> "활동";
			case CODE_QUALITY -> "코드";
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

	private List<AiAnalysisResponse.CoachingItem> nextActions(
			AiAnalysisRequest request,
			List<String> citedEvidence
	) {
		if (citedEvidence.isEmpty()) {
			return List.of();
		}
		String fact = request.repositories().getFirst().evidence().getFirst().factKey();
		return List.of(new AiAnalysisResponse.CoachingItem(
				fact + " 근거를 포트폴리오 설명에 더 구체적으로 연결하세요.",
				List.of(citedEvidence.getFirst())
		));
	}

	private AiAnalysisResponse.JobAppeal jobAppeal(AiAnalysisRequest request, List<String> citedEvidence) {
		if (citedEvidence.isEmpty()) {
			throw new IllegalStateException("jobAppeal에 사용할 Evidence가 없습니다.");
		}
		return new AiAnalysisResponse.JobAppeal(
				request.targetJob() + " " + request.targetCareerLevel()
						+ " 지원자에게 전달된 Evidence만으로 구조와 문서를 어필할 수 있습니다.",
				List.of(citedEvidence.getFirst())
		);
	}

	private List<AiAnalysisResponse.PortfolioStatement> portfolioStatements(
			AiAnalysisRequest request,
			List<String> citedEvidence
	) {
		List<AiAnalysisResponse.PortfolioStatement> statements = new ArrayList<>();
		for (AiAnalysisRequest.Repository repository : request.repositories()) {
			for (AiAnalysisRequest.UserClaim claim : repository.userClaims()) {
				statements.add(new AiAnalysisResponse.PortfolioStatement(
						claim.statement(),
						citedEvidence.isEmpty() ? List.of() : List.of(citedEvidence.getFirst()),
						List.of(claim.claimId())
				));
			}
		}
		if (statements.isEmpty() && !citedEvidence.isEmpty()) {
			statements.add(new AiAnalysisResponse.PortfolioStatement(
					"저장소 Evidence를 바탕으로 역할을 문장으로 정리하세요.",
					List.of(citedEvidence.getFirst()),
					List.of()
			));
		}
		return statements;
	}

	private List<String> firstEvidenceIds(AiAnalysisRequest request) {
		return request.repositories().stream()
				.flatMap(repository -> repository.evidence().stream())
				.map(AiAnalysisRequest.Evidence::evidenceId)
				.limit(3)
				.toList();
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

	private List<AiAnalysisResponse.InterviewQuestion> interviewQuestions(
			AiAnalysisRequest request,
			List<AnalysisDepth> usedLevels,
			List<String> citedEvidence
	) {
		if (citedEvidence.isEmpty()) {
			return List.of();
		}
		String repositoryName = request.repositories().stream()
				.map(AiAnalysisRequest.Repository::repositoryFullName)
				.findFirst()
				.orElse("선택한 저장소");
		List<String> claimRefs = request.repositories().stream()
				.flatMap(repository -> repository.userClaims().stream())
				.map(AiAnalysisRequest.UserClaim::claimId)
				.limit(1)
				.toList();
		if (usedLevels.contains(AnalysisDepth.P1)) {
			String activityId = request.repositories().stream()
					.flatMap(repository -> repository.evidence().stream())
					.filter(item -> item.analysisDepth() == AnalysisDepth.P1)
					.map(AiAnalysisRequest.Evidence::evidenceId)
					.findFirst()
					.orElse(citedEvidence.getFirst());
			return List.of(new AiAnalysisResponse.InterviewQuestion(
					"가장 임팩트가 큰 커밋이나 PR에서 본인이 맡은 역할을 근거와 함께 설명하시겠어요?",
					"활동 Evidence와 UserClaim이 같은 기여를 가리키는지 확인합니다.",
					"커밋/PR Evidence를 먼저 말한 뒤, 본인 주장을 그 근거에 연결하면 됩니다.",
					List.of(activityId),
					claimRefs
			));
		}
		return List.of(new AiAnalysisResponse.InterviewQuestion(
				repositoryName + "의 디렉터리 구조와 빌드 도구를 어떻게 설명하시겠어요?",
				"P0 Evidence만으로 구조와 스택을 설명하는지 확인합니다.",
				"파일 트리와 빌드 매니페스트 Evidence를 기준으로 설명하면 됩니다.",
				List.of(citedEvidence.getFirst()),
				List.of()
		));
	}
}

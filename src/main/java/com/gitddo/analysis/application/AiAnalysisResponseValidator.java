package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.contract.FindingCategory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiAnalysisResponseValidator {

	private static final Pattern FINDING_ID = Pattern.compile("^find_[0-9]{3,}$");
	private static final Pattern EVIDENCE_ID = Pattern.compile("^ev_[0-9]{3,}$");
	private static final Pattern CLAIM_ID = Pattern.compile("^claim_[0-9]{3,}$");

	public void validate(AiAnalysisRequest request, AiAnalysisResponse response) {
		if (response == null) {
			throw invalid("AI 응답이 비어 있습니다.");
		}
		if (!AiAnalysisResponse.SCHEMA_VERSION.equals(response.schemaVersion())) {
			throw invalid("schemaVersion이 요청 계약과 다릅니다.");
		}
		if (response.evaluatorVersion() == null || response.evaluatorVersion().isBlank()) {
			throw invalid("evaluatorVersion이 없습니다.");
		}
		if (isBlank(response.analysisId()) || !request.analysisId().equals(response.analysisId())) {
			throw invalid("analysisId가 요청과 일치하지 않습니다.");
		}
		if (response.requestedAnalysisDepth() != request.requestedAnalysisDepth()) {
			throw invalid("requestedAnalysisDepth가 요청과 일치하지 않습니다.");
		}
		if (isBlank(response.summary())) {
			throw invalid("summary가 없습니다.");
		}
		if (response.coaching() == null) {
			throw invalid("coaching이 없습니다.");
		}

		Set<AnalysisDepth> completed = EnumSet.noneOf(AnalysisDepth.class);
		Map<String, AiAnalysisRequest.Repository> requestById = new HashMap<>();
		Set<String> allEvidenceIds = new HashSet<>();
		for (AiAnalysisRequest.Repository repository : request.repositories()) {
			requestById.put(repository.repositoryId(), repository);
			completed.addAll(repository.completedEvidenceLevels());
			repository.evidence().forEach(evidence -> allEvidenceIds.add(evidence.evidenceId()));
		}

		validateUsedLevels(request.requestedAnalysisDepth(), completed, response.usedEvidenceLevels());
		validateRepositories(request, response, requestById);
		validateCoachingRefs(response, allEvidenceIds);
		validateFindingIds(response);
	}

	private void validateUsedLevels(
			AnalysisDepth requested,
			Set<AnalysisDepth> completed,
			List<AnalysisDepth> usedLevels
	) {
		if (usedLevels == null || usedLevels.isEmpty()) {
			throw invalid("usedEvidenceLevels가 없습니다.");
		}
		Set<AnalysisDepth> unique = EnumSet.noneOf(AnalysisDepth.class);
		int maxUsed = -1;
		for (AnalysisDepth depth : usedLevels) {
			if (depth == null || !unique.add(depth)) {
				throw invalid("usedEvidenceLevels에 중복이 있습니다.");
			}
			if (!completed.contains(depth)) {
				throw invalid("수집되지 않은 Evidence 수준을 사용했습니다: " + depth);
			}
			maxUsed = Math.max(maxUsed, rank(depth));
		}
		if (maxUsed > rank(requested)) {
			throw invalid("요청한 분석 깊이보다 깊은 근거를 사용했습니다.");
		}
	}

	private void validateRepositories(
			AiAnalysisRequest request,
			AiAnalysisResponse response,
			Map<String, AiAnalysisRequest.Repository> requestById
	) {
		if (response.repositories().size() != request.repositories().size()) {
			throw invalid("저장소 수가 요청과 일치하지 않습니다.");
		}
		Set<String> seen = new HashSet<>();
		for (AiAnalysisResponse.RepositoryReport repository : response.repositories()) {
			if (repository == null || isBlank(repository.repositoryId()) || !seen.add(repository.repositoryId())) {
				throw invalid("응답 저장소 ID가 없거나 중복되었습니다.");
			}
			AiAnalysisRequest.Repository requested = requestById.get(repository.repositoryId());
			if (requested == null) {
				throw invalid("요청에 없는 저장소입니다: " + repository.repositoryId());
			}
			if (!requested.repositoryFullName().equals(repository.repositoryFullName())) {
				throw invalid("repositoryFullName이 요청과 일치하지 않습니다.");
			}
			if (requested.snapshotHashAlgorithm() != repository.snapshotHashAlgorithm()) {
				throw invalid("snapshotHashAlgorithm이 요청과 일치하지 않습니다.");
			}
			if (isBlank(repository.snapshotSha())
					|| !requested.snapshotSha().equals(repository.snapshotSha())) {
				throw invalid("snapshotSha가 요청과 일치하지 않습니다.");
			}
			Set<String> evidenceIds = new HashSet<>();
			Map<String, AnalysisDepth> evidenceDepths = new HashMap<>();
			requested.evidence().forEach(evidence -> {
				evidenceIds.add(evidence.evidenceId());
				evidenceDepths.put(evidence.evidenceId(), evidence.analysisDepth());
			});
			Set<String> claimIds = new HashSet<>();
			requested.userClaims().forEach(claim -> claimIds.add(claim.claimId()));
			for (AiAnalysisResponse.Finding finding : repository.findings()) {
				validateFinding(finding, evidenceIds, evidenceDepths, claimIds, response.usedEvidenceLevels());
			}
		}
		if (!seen.equals(requestById.keySet())) {
			throw invalid("응답 저장소 목록이 요청과 일치하지 않습니다.");
		}
	}

	private void validateFinding(
			AiAnalysisResponse.Finding finding,
			Set<String> evidenceIds,
			Map<String, AnalysisDepth> evidenceDepths,
			Set<String> claimIds,
			List<AnalysisDepth> usedLevels
	) {
		if (finding == null || isBlank(finding.findingId()) || !FINDING_ID.matcher(finding.findingId()).matches()) {
			throw invalid("findingId 형식이 올바르지 않습니다.");
		}
		if (finding.category() == null || finding.severity() == null) {
			throw invalid("finding category 또는 severity가 없습니다.");
		}
		if (isBlank(finding.title()) || isBlank(finding.detail())) {
			throw invalid("finding 제목 또는 내용이 없습니다.");
		}
		if (!allowedCategories(usedLevels).contains(finding.category())) {
			throw invalid("사용한 근거 수준으로 해석할 수 없는 항목입니다: " + finding.category());
		}
		for (String evidenceId : finding.evidenceRefs()) {
			if (!EVIDENCE_ID.matcher(evidenceId).matches() || !evidenceIds.contains(evidenceId)) {
				throw invalid("요청에 없는 Evidence ID를 인용했습니다: " + evidenceId);
			}
			AnalysisDepth depth = evidenceDepths.get(evidenceId);
			if (depth != null && !usedLevels.contains(depth)) {
				throw invalid("usedEvidenceLevels에 없는 Evidence를 인용했습니다: " + evidenceId);
			}
		}
		for (String claimId : finding.claimRefs()) {
			if (!CLAIM_ID.matcher(claimId).matches() || !claimIds.contains(claimId)) {
				throw invalid("요청에 없는 UserClaim ID를 인용했습니다: " + claimId);
			}
		}
	}

	private void validateCoachingRefs(AiAnalysisResponse response, Set<String> allEvidenceIds) {
		validateItems(response.coaching().strengths(), allEvidenceIds);
		validateItems(response.coaching().gaps(), allEvidenceIds);
		validateItems(response.coaching().nextActions(), allEvidenceIds);
		for (String question : response.coaching().interviewQuestions()) {
			if (isBlank(question)) {
				throw invalid("interviewQuestions에 빈 항목이 있습니다.");
			}
		}
	}

	private void validateItems(
			List<AiAnalysisResponse.CoachingItem> items,
			Set<String> allEvidenceIds
	) {
		for (AiAnalysisResponse.CoachingItem item : items) {
			if (item == null || isBlank(item.text())) {
				throw invalid("coaching 항목이 비어 있습니다.");
			}
			for (String evidenceId : item.evidenceRefs()) {
				if (!allEvidenceIds.contains(evidenceId)) {
					throw invalid("요청에 없는 Evidence ID를 인용했습니다: " + evidenceId);
				}
			}
		}
	}

	private void validateFindingIds(AiAnalysisResponse response) {
		Set<String> ids = new HashSet<>();
		for (AiAnalysisResponse.RepositoryReport repository : response.repositories()) {
			for (AiAnalysisResponse.Finding finding : repository.findings()) {
				if (!ids.add(finding.findingId())) {
					throw invalid("findingId가 중복되었습니다: " + finding.findingId());
				}
			}
		}
	}

	private Set<FindingCategory> allowedCategories(List<AnalysisDepth> usedLevels) {
		Set<FindingCategory> allowed = EnumSet.of(
				FindingCategory.STRUCTURE,
				FindingCategory.DOCUMENTATION,
				FindingCategory.STACK
		);
		if (usedLevels.contains(AnalysisDepth.P1)) {
			allowed.add(FindingCategory.ACTIVITY);
			allowed.add(FindingCategory.CONTRIBUTION);
		}
		if (usedLevels.contains(AnalysisDepth.P2)) {
			allowed.add(FindingCategory.CODE_QUALITY);
		}
		return allowed;
	}

	private int rank(AnalysisDepth depth) {
		return switch (depth) {
			case P0 -> 0;
			case P1 -> 1;
			case P2 -> 2;
		};
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private InvalidAiAnalysisResponseException invalid(String message) {
		return new InvalidAiAnalysisResponseException(message);
	}
}

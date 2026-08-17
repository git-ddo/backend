package com.gitddo.analysis.domain;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.portfolio.domain.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_runs")
public class EvaluationRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "analysis_id", nullable = false, unique = true, updatable = false)
	private UUID analysisId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@Column(nullable = false)
	private int sequence;

	@Column(name = "portfolio_version", nullable = false)
	private long portfolioVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EvaluationStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
	private EvaluationInputSnapshot inputSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "evidence_snapshot", columnDefinition = "jsonb")
	private P0EvidenceSnapshot evidenceSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "ai_request", columnDefinition = "jsonb")
	private AiAnalysisRequest aiRequest;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String result;

	@Column(name = "evaluator_version", length = 100)
	private String evaluatorVersion;

	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected EvaluationRun() {
	}

	public EvaluationRun(
			Portfolio portfolio,
			int sequence,
			EvaluationInputSnapshot inputSnapshot,
			String evaluatorVersion
	) {
		if (sequence < 1) {
			throw new IllegalArgumentException("평가 순번은 1 이상이어야 합니다.");
		}
		this.portfolio = portfolio;
		this.analysisId = UUID.randomUUID();
		this.sequence = sequence;
		this.portfolioVersion = portfolio.getVersion();
		this.status = EvaluationStatus.REQUESTED;
		this.inputSnapshot = inputSnapshot;
		this.evaluatorVersion = evaluatorVersion;
		this.requestedAt = Instant.now();
	}

	public void startCollection() {
		requireStatus(EvaluationStatus.REQUESTED);
		this.status = EvaluationStatus.COLLECTING;
		this.startedAt = Instant.now();
	}

	public void completeEvidenceCollection(
			P0EvidenceSnapshot evidenceSnapshot,
			AiAnalysisRequest aiRequest
	) {
		requireStatus(EvaluationStatus.COLLECTING);
		if (evidenceSnapshot == null) {
			throw new IllegalArgumentException("P0 Evidence 스냅샷은 필수입니다.");
		}
		if (aiRequest == null) {
			throw new IllegalArgumentException("AI 요청 본문은 필수입니다.");
		}
		this.evidenceSnapshot = evidenceSnapshot;
		this.aiRequest = aiRequest;
		this.status = EvaluationStatus.EVIDENCE_READY;
	}

	public void startAnalysis() {
		requireStatus(EvaluationStatus.EVIDENCE_READY);
		this.status = EvaluationStatus.ANALYZING;
	}

	public void succeed(String result) {
		requireStatus(EvaluationStatus.ANALYZING);
		if (result == null || result.isBlank()) {
			throw new IllegalArgumentException("평가 결과는 필수입니다.");
		}
		this.status = EvaluationStatus.SUCCEEDED;
		this.result = result;
		this.completedAt = Instant.now();
	}

	public void fail(String failureReason) {
		if (status == EvaluationStatus.SUCCEEDED || status == EvaluationStatus.FAILED) {
			throw new IllegalStateException("완료된 평가는 실패 상태로 변경할 수 없습니다.");
		}
		this.status = EvaluationStatus.FAILED;
		this.failureReason = failureReason == null || failureReason.isBlank()
				? "평가 처리에 실패했습니다."
				: failureReason;
		this.completedAt = Instant.now();
	}

	private void requireStatus(EvaluationStatus expected) {
		if (this.status != expected) {
			throw new IllegalStateException(
					"평가 상태가 " + expected + "일 때만 수행할 수 있습니다."
			);
		}
	}

	public Long getId() {
		return id;
	}

	public UUID getAnalysisId() {
		return analysisId;
	}

	public int getSequence() {
		return sequence;
	}

	public long getPortfolioVersion() {
		return portfolioVersion;
	}

	public EvaluationStatus getStatus() {
		return status;
	}

	public EvaluationInputSnapshot getInputSnapshot() {
		return inputSnapshot;
	}

	public P0EvidenceSnapshot getEvidenceSnapshot() {
		return evidenceSnapshot;
	}

	public AiAnalysisRequest getAiRequest() {
		return aiRequest;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}

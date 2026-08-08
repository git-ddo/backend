package com.gitddo.analysis.domain;

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

@Entity
@Table(name = "evaluation_runs")
public class EvaluationRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

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
		this.sequence = sequence;
		this.portfolioVersion = portfolio.getVersion();
		this.status = EvaluationStatus.REQUESTED;
		this.inputSnapshot = inputSnapshot;
		this.evaluatorVersion = evaluatorVersion;
		this.requestedAt = Instant.now();
	}

	public void start() {
		requireStatus(EvaluationStatus.REQUESTED);
		this.status = EvaluationStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	public void succeed(String result) {
		requireStatus(EvaluationStatus.RUNNING);
		if (result == null || result.isBlank()) {
			throw new IllegalArgumentException("평가 결과는 필수입니다.");
		}
		this.status = EvaluationStatus.SUCCEEDED;
		this.result = result;
		this.completedAt = Instant.now();
	}

	public void fail(String failureReason) {
		requireStatus(EvaluationStatus.RUNNING);
		this.status = EvaluationStatus.FAILED;
		this.failureReason = failureReason;
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
}

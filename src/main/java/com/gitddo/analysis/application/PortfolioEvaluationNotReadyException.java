package com.gitddo.analysis.application;

public class PortfolioEvaluationNotReadyException extends RuntimeException {

	public PortfolioEvaluationNotReadyException() {
		super("평가를 요청하려면 포트폴리오에 저장소를 1개 이상 추가해야 합니다.");
	}
}

package com.gitddo.analysis.application;

public class UnsupportedAnalysisCombinationException extends RuntimeException {

	public UnsupportedAnalysisCombinationException() {
		super("현재 MVP는 BACKEND × P0 분석만 지원합니다.");
	}
}

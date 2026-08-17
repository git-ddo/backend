package com.gitddo.analysis.application;

public class EvaluationNotFoundException extends RuntimeException {

	public EvaluationNotFoundException() {
		super("평가 실행을 찾을 수 없습니다.");
	}
}

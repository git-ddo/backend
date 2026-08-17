package com.gitddo.portfolio.application;

public class PortfolioRepositoryNotFoundException extends RuntimeException {

	public PortfolioRepositoryNotFoundException(Long repositoryId) {
		super("포트폴리오에 포함된 저장소를 찾을 수 없습니다: " + repositoryId);
	}
}

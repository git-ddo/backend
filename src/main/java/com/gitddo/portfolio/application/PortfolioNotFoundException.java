package com.gitddo.portfolio.application;

public class PortfolioNotFoundException extends RuntimeException {

	public PortfolioNotFoundException() {
		super("포트폴리오를 찾을 수 없습니다.");
	}
}

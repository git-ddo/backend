package com.gitddo.portfolio.application;

public class PortfolioVersionConflictException extends RuntimeException {

	public PortfolioVersionConflictException() {
		super("포트폴리오가 다른 요청에서 수정되었습니다. 최신 내용을 다시 조회해 주세요.");
	}
}

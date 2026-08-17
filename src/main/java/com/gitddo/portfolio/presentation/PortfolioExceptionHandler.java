package com.gitddo.portfolio.presentation;

import com.gitddo.portfolio.application.PortfolioNotFoundException;
import com.gitddo.portfolio.application.PortfolioRepositoryNotFoundException;
import com.gitddo.portfolio.application.PortfolioVersionConflictException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PortfolioController.class)
public class PortfolioExceptionHandler {

	@ExceptionHandler(PortfolioNotFoundException.class)
	ResponseEntity<PortfolioApiErrorResponse> handleNotFound(PortfolioNotFoundException exception) {
		return response(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(PortfolioRepositoryNotFoundException.class)
	ResponseEntity<PortfolioApiErrorResponse> handleRepositoryNotFound(
			PortfolioRepositoryNotFoundException exception
	) {
		return response(
				HttpStatus.NOT_FOUND,
				"PORTFOLIO_REPOSITORY_NOT_FOUND",
				exception.getMessage()
		);
	}

	@ExceptionHandler({
			PortfolioVersionConflictException.class,
			OptimisticLockingFailureException.class
	})
	ResponseEntity<PortfolioApiErrorResponse> handleConflict(RuntimeException exception) {
		return response(
				HttpStatus.CONFLICT,
				"PORTFOLIO_VERSION_CONFLICT",
				"포트폴리오가 다른 요청에서 수정되었습니다. 최신 내용을 다시 조회해 주세요."
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<PortfolioApiErrorResponse> handleBadRequest(IllegalArgumentException exception) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_PORTFOLIO", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<PortfolioApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception
	) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.orElse("요청값이 올바르지 않습니다.");
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<PortfolioApiErrorResponse> handleUnreadableRequest(
			HttpMessageNotReadableException exception
	) {
		return response(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST",
				"요청 형식 또는 enum 값이 올바르지 않습니다."
		);
	}

	private ResponseEntity<PortfolioApiErrorResponse> response(
			HttpStatus status,
			String code,
			String message
	) {
		return ResponseEntity.status(status)
				.body(new PortfolioApiErrorResponse(code, message));
	}
}

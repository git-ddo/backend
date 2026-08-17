package com.gitddo.analysis.presentation;

import com.gitddo.analysis.application.EvaluationNotFoundException;
import com.gitddo.analysis.application.PortfolioEvaluationNotReadyException;
import com.gitddo.portfolio.application.PortfolioNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EvaluationController.class)
public class EvaluationExceptionHandler {

	@ExceptionHandler({
			EvaluationNotFoundException.class,
			PortfolioNotFoundException.class
	})
	ResponseEntity<EvaluationApiErrorResponse> handleNotFound(RuntimeException exception) {
		return response(HttpStatus.NOT_FOUND, "EVALUATION_RESOURCE_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(PortfolioEvaluationNotReadyException.class)
	ResponseEntity<EvaluationApiErrorResponse> handleNotReady(
			PortfolioEvaluationNotReadyException exception
	) {
		return response(
				HttpStatus.BAD_REQUEST,
				"PORTFOLIO_EVALUATION_NOT_READY",
				exception.getMessage()
		);
	}

	private ResponseEntity<EvaluationApiErrorResponse> response(
			HttpStatus status,
			String code,
			String message
	) {
		return ResponseEntity.status(status)
				.body(new EvaluationApiErrorResponse(code, message));
	}
}

package com.gitddo.analysis.client;

import com.gitddo.analysis.contract.AiErrorCode;

public class AiAnalysisClientException extends RuntimeException {

	private final AiErrorCode code;
	private final Boolean retryable;

	public AiAnalysisClientException(String message) {
		this(message, null, null, null);
	}

	public AiAnalysisClientException(String message, Throwable cause) {
		this(message, cause, null, null);
	}

	public AiAnalysisClientException(String message, Throwable cause, AiErrorCode code, Boolean retryable) {
		super(message, cause);
		this.code = code;
		this.retryable = retryable;
	}

	public AiErrorCode code() {
		return code;
	}

	public Boolean retryable() {
		return retryable;
	}
}

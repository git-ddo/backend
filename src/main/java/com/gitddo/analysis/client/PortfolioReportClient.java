package com.gitddo.analysis.client;

import com.gitddo.analysis.contract.AiAnalysisRequest;
import com.gitddo.analysis.contract.AiAnalysisResponse;

public interface PortfolioReportClient {

	AiAnalysisResponse requestReport(AiAnalysisRequest request);
}

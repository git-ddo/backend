package com.gitddo.analysis.client;

import com.gitddo.analysis.AnalysisContractFixtures;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPortfolioReportClientTests {

	@Test
	void postsRequestToInternalReportsPath() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.baseUrl("http://ai.example").build();
		HttpPortfolioReportClient client = new HttpPortfolioReportClient(
				restClient,
				"/internal/v1/portfolio-reports"
		);

		server.expect(requestTo("http://ai.example/internal/v1/portfolio-reports"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andRespond(withSuccess("""
						{
						  "schemaVersion": "1.0",
						  "analysisId": "11111111-1111-4111-8111-111111111111",
						  "evaluatorVersion": "ai-1.0",
						  "requestedAnalysisDepth": "P0",
						  "usedEvidenceLevels": ["P0"],
						  "summary": "P0만 해석했습니다.",
						  "repositories": [
						    {
						      "repositoryId": "123",
						      "repositoryFullName": "git-ddo/backend",
						      "snapshotHashAlgorithm": "SHA1",
						      "snapshotSha": "commit-sha",
						      "findings": []
						    }
						  ],
						  "coaching": {
						    "strengths": [],
						    "gaps": [],
						    "nextActions": [],
						    "interviewQuestions": ["구조는 어떻게 설명하나요?"]
						  },
						  "limitations": []
						}
						""", MediaType.APPLICATION_JSON));

		AiAnalysisResponse response = client.requestReport(AnalysisContractFixtures.p0Request());

		assertThat(response.analysisId()).isEqualTo(AnalysisContractFixtures.ANALYSIS_ID);
		assertThat(response.evaluatorVersion()).isEqualTo("ai-1.0");
		server.verify();
	}

	@Test
	void wrapsHttpErrors() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.baseUrl("http://ai.example").build();
		HttpPortfolioReportClient client = new HttpPortfolioReportClient(
				restClient,
				"/internal/v1/portfolio-reports"
		);
		server.expect(requestTo("http://ai.example/internal/v1/portfolio-reports"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.requestReport(AnalysisContractFixtures.p0Request()))
				.isInstanceOf(AiAnalysisClientException.class)
				.hasMessageContaining("500");
	}
}

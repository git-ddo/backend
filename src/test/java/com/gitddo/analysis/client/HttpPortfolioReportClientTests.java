package com.gitddo.analysis.client;

import com.gitddo.analysis.AnalysisContractFixtures;
import com.gitddo.analysis.contract.AiAnalysisResponse;
import com.gitddo.analysis.contract.AiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPortfolioReportClientTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void postsRequestToInternalReportsPath() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.baseUrl("http://ai.example").build();
		HttpPortfolioReportClient client = new HttpPortfolioReportClient(
				restClient,
				objectMapper,
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
						    "nextActions": [
						      {
						        "text": "README에 실행 방법을 구체적으로 적으세요.",
						        "evidenceRefs": ["ev_001"]
						      }
						    ],
						    "jobAppeal": {
						      "text": "문서 Evidence로 학습 경험을 어필할 수 있습니다.",
						      "evidenceRefs": ["ev_001"]
						    },
						    "portfolioStatements": [],
						    "interviewQuestions": [
						      {
						        "question": "구조는 어떻게 설명하나요?",
						        "intent": "P0 구조 근거를 말로 재구성하는지 확인합니다.",
						        "answerGuide": "파일 트리와 README를 기준으로 설명하면 됩니다.",
						        "evidenceRefs": ["ev_001"],
						        "claimRefs": []
						      }
						    ]
						  },
						  "limitations": []
						}
						""", MediaType.APPLICATION_JSON));

		AiAnalysisResponse response = client.requestReport(AnalysisContractFixtures.p0Request());

		assertThat(response.analysisId()).isEqualTo(AnalysisContractFixtures.ANALYSIS_ID);
		assertThat(response.evaluatorVersion()).isEqualTo("ai-1.0");
		assertThat(response.coaching().jobAppeal().text()).contains("Evidence");
		assertThat(response.coaching().interviewQuestions().getFirst().question()).contains("구조");
		server.verify();
	}

	@Test
	void wrapsHttpErrors() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.baseUrl("http://ai.example").build();
		HttpPortfolioReportClient client = new HttpPortfolioReportClient(
				restClient,
				objectMapper,
				"/internal/v1/portfolio-reports"
		);
		server.expect(requestTo("http://ai.example/internal/v1/portfolio-reports"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.requestReport(AnalysisContractFixtures.p0Request()))
				.isInstanceOf(AiAnalysisClientException.class)
				.hasMessageContaining("500");
	}

	@Test
	void parsesErrorEnvelopeFromNon2xxResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.baseUrl("http://ai.example").build();
		HttpPortfolioReportClient client = new HttpPortfolioReportClient(
				restClient,
				objectMapper,
				"/internal/v1/portfolio-reports"
		);
		server.expect(requestTo("http://ai.example/internal/v1/portfolio-reports"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{
								  "schemaVersion": "1.0",
								  "analysisId": "11111111-1111-4111-8111-111111111111",
								  "code": "LLM_RATE_LIMITED",
								  "message": "모델 호출 한도를 초과했습니다.",
								  "retryable": true,
								  "details": { "retryAfterSeconds": 30 }
								}
								"""));

		assertThatThrownBy(() -> client.requestReport(AnalysisContractFixtures.p0Request()))
				.isInstanceOf(AiAnalysisClientException.class)
				.hasMessageContaining("LLM_RATE_LIMITED")
				.hasMessageContaining("retryable=true")
				.satisfies(exception -> {
					AiAnalysisClientException clientException = (AiAnalysisClientException) exception;
					assertThat(clientException.code()).isEqualTo(AiErrorCode.LLM_RATE_LIMITED);
					assertThat(clientException.retryable()).isTrue();
				});
	}
}

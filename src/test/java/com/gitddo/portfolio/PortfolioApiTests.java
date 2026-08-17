package com.gitddo.portfolio;

import com.gitddo.TestcontainersConfiguration;
import com.gitddo.analysis.application.PortfolioEvaluationNotReadyException;
import com.gitddo.analysis.application.EvaluationService;
import com.gitddo.analysis.domain.EvaluationRun;
import com.gitddo.analysis.domain.EvaluationRunRepository;
import com.gitddo.github.client.GithubRepositoryPayload;
import com.gitddo.github.domain.GithubRepository;
import com.gitddo.github.domain.GithubRepositoryRepository;
import com.gitddo.github.domain.UserRepositoryAccess;
import com.gitddo.github.domain.UserRepositoryAccessRepository;
import com.gitddo.member.domain.GithubUser;
import com.gitddo.member.domain.GithubUserRepository;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class PortfolioApiTests {

	private static final AtomicLong IDS = new AtomicLong(1_000_000L);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GithubUserRepository githubUserRepository;

	@Autowired
	private GithubRepositoryRepository githubRepositoryRepository;

	@Autowired
	private UserRepositoryAccessRepository userRepositoryAccessRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private EvaluationService evaluationService;

	@Autowired
	private EvaluationRunRepository evaluationRunRepository;

	private GithubUser user;
	private GithubRepository repository;

	@BeforeEach
	void setUp() {
		long id = IDS.incrementAndGet();
		user = githubUserRepository.save(
				new GithubUser(id, "portfolio-user-" + id, null)
		);
		repository = createAccessibleRepository(user, IDS.incrementAndGet(), "backend-api");
	}

	@Test
	void createsReadsUpdatesAndDeletesPortfolio() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(repository.getGithubId(), "초기 포트폴리오")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("초기 포트폴리오"))
				.andExpect(jsonPath("$.repositories.length()").value(1))
				.andExpect(jsonPath("$.repositories[0].roles[0].primary").value(true));

		Portfolio saved = portfolioRepository
				.findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.getGithubId())
				.getFirst();

		mockMvc.perform(get("/api/v1/portfolios/{id}", saved.getId())
						.with(oauthUser(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0));

		mockMvc.perform(put("/api/v1/portfolios/{id}", saved.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateRequest(repository.getGithubId(), saved.getVersion())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("수정된 포트폴리오"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(delete("/api/v1/portfolios/{id}", saved.getId())
						.with(oauthUser(user))
						.with(csrf()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/portfolios/{id}", saved.getId())
						.with(oauthUser(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void allowsEmptyPortfolioAndRejectsMoreThanFiveRepositories() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestWithRepositories("[]")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.repositories.length()").value(0));

		List<GithubRepository> repositories = new ArrayList<>();
		repositories.add(repository);
		for (int index = 1; index < 6; index++) {
			repositories.add(createAccessibleRepository(
					user,
					IDS.incrementAndGet(),
					"repository-" + index
			));
		}
		String entries = repositories.stream()
				.map(repo -> repositoryJson(repo.getGithubId(), true))
				.collect(java.util.stream.Collectors.joining(","));

		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestWithRepositories("[" + entries + "]")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInaccessibleRepositoryAndInvalidPrimaryRole() throws Exception {
		GithubUser otherUser = githubUserRepository.save(
				new GithubUser(IDS.incrementAndGet(), "other-user", null)
		);
		GithubRepository inaccessible = createAccessibleRepository(
				otherUser,
				IDS.incrementAndGet(),
				"private-to-selection"
		);

		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(inaccessible.getGithubId(), "접근 불가")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PORTFOLIO"));

		String invalidRole = """
				{
				  "repositoryId": %d,
				  "contributionDescription": "API를 구현했습니다.",
				  "roles": [
				    {"roleType": "BACKEND", "participationLevel": "LEAD", "primary": false}
				  ]
				}
				""".formatted(repository.getGithubId());
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestWithRepositories("[" + invalidRole + "]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("대표 역할은 정확히 1개여야 합니다."));
	}

	@Test
	void hidesAnotherUsersPortfolioAndRejectsStaleVersion() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(repository.getGithubId(), "소유권 테스트")))
				.andExpect(status().isCreated());

		Portfolio saved = portfolioRepository
				.findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.getGithubId())
				.getFirst();
		GithubUser otherUser = githubUserRepository.save(
				new GithubUser(IDS.incrementAndGet(), "unauthorized-user", null)
		);

		mockMvc.perform(get("/api/v1/portfolios/{id}", saved.getId())
						.with(oauthUser(otherUser)))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/v1/portfolios/{id}", saved.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateRequest(repository.getGithubId(), 999L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PORTFOLIO_VERSION_CONFLICT"));
	}

	@Test
	void preservesEvaluationSnapshotAfterPortfolioIsUpdated() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(repository.getGithubId(), "평가 당시 제목")))
				.andExpect(status().isCreated());

		Portfolio portfolio = portfolioRepository
				.findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.getGithubId())
				.getFirst();
		EvaluationRun requested = evaluationService.request(
				user.getGithubId(),
				portfolio.getId(),
				"coach-v1"
		);

		mockMvc.perform(put("/api/v1/portfolios/{id}", portfolio.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateRequest(repository.getGithubId(), portfolio.getVersion())))
				.andExpect(status().isOk());

		EvaluationRun reloaded = evaluationRunRepository.findById(requested.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getSequence()).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(reloaded.getPortfolioVersion()).isZero();
		org.assertj.core.api.Assertions.assertThat(reloaded.getInputSnapshot().title())
				.isEqualTo("평가 당시 제목");
		org.assertj.core.api.Assertions.assertThat(reloaded.getInputSnapshot().repositories())
				.singleElement()
				.extracting(repositorySnapshot -> repositorySnapshot.contributionDescription())
				.isEqualTo("핵심 API와 데이터 모델을 구현했습니다.");
	}

	@Test
	void addsUpdatesAndRemovesSelectedRepository() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(repository.getGithubId(), "저장소 관리")))
				.andExpect(status().isCreated());
		Portfolio portfolio = portfolioRepository
				.findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.getGithubId())
				.getFirst();
		GithubRepository selected = createAccessibleRepository(
				user,
				IDS.incrementAndGet(),
				"selected-repository"
		);

		mockMvc.perform(post("/api/v1/portfolios/{id}/repositories", portfolio.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(addRepositoryRequest(selected.getGithubId(), 0L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.repositories.length()").value(2))
				.andExpect(jsonPath("$.repositories[1].repositoryId")
						.value(selected.getGithubId()));

		mockMvc.perform(put(
							"/api/v1/portfolios/{portfolioId}/repositories/{repositoryId}",
							portfolio.getId(),
							selected.getGithubId()
						)
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateRepositoryEntryRequest(1L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.repositories[1].contributionDescription")
						.value("검색 화면과 상태 관리를 구현했습니다."))
				.andExpect(jsonPath("$.repositories[1].roles[0].roleType")
						.value("FRONTEND"));

		mockMvc.perform(delete(
							"/api/v1/portfolios/{portfolioId}/repositories/{repositoryId}",
							portfolio.getId(),
							selected.getGithubId()
						)
						.param("version", "2")
						.with(oauthUser(user))
						.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(3))
				.andExpect(jsonPath("$.repositories.length()").value(1));

		mockMvc.perform(delete(
							"/api/v1/portfolios/{portfolioId}/repositories/{repositoryId}",
							portfolio.getId(),
							repository.getGithubId()
						)
						.param("version", "3")
						.with(oauthUser(user))
						.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(4))
				.andExpect(jsonPath("$.repositories.length()").value(0));

		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
						evaluationService.request(user.getGithubId(), portfolio.getId(), "coach-v1"))
				.isInstanceOf(PortfolioEvaluationNotReadyException.class)
				.hasMessage("평가를 요청하려면 포트폴리오에 저장소를 1개 이상 추가해야 합니다.");
	}

	@Test
	void rejectsDuplicateInaccessibleAndSixthRepository() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(repository.getGithubId(), "추가 제한")))
				.andExpect(status().isCreated());
		Portfolio portfolio = portfolioRepository
				.findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.getGithubId())
				.getFirst();

		mockMvc.perform(post("/api/v1/portfolios/{id}/repositories", portfolio.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(addRepositoryRequest(repository.getGithubId(), 0L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("이미 포트폴리오에 추가된 저장소입니다."));

		GithubUser other = githubUserRepository.save(
				new GithubUser(IDS.incrementAndGet(), "repository-other", null)
		);
		GithubRepository inaccessible = createAccessibleRepository(
				other,
				IDS.incrementAndGet(),
				"inaccessible"
		);
		mockMvc.perform(post("/api/v1/portfolios/{id}/repositories", portfolio.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(addRepositoryRequest(inaccessible.getGithubId(), 0L)))
				.andExpect(status().isBadRequest());

		long version = 0L;
		for (int index = 0; index < 4; index++) {
			GithubRepository additional = createAccessibleRepository(
					user,
					IDS.incrementAndGet(),
					"limit-" + index
			);
			mockMvc.perform(post("/api/v1/portfolios/{id}/repositories", portfolio.getId())
							.with(oauthUser(user))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(addRepositoryRequest(additional.getGithubId(), version)))
					.andExpect(status().isOk());
			version++;
		}

		GithubRepository sixth = createAccessibleRepository(
				user,
				IDS.incrementAndGet(),
				"sixth"
		);
		mockMvc.perform(post("/api/v1/portfolios/{id}/repositories", portfolio.getId())
						.with(oauthUser(user))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(addRepositoryRequest(sixth.getGithubId(), version)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("포트폴리오 저장소는 최대 5개까지 추가할 수 있습니다."));
	}

	private GithubRepository createAccessibleRepository(
			GithubUser owner,
			long githubRepositoryId,
			String name
	) {
		GithubRepository created = githubRepositoryRepository.save(
				new GithubRepository(new GithubRepositoryPayload(
						githubRepositoryId,
						name,
						"git-ddo/" + name,
						"테스트 저장소",
						"https://github.com/git-ddo/" + name,
						"Java",
						false,
						false,
						"main",
						1,
						0,
						Instant.parse("2026-08-01T00:00:00Z"),
						Instant.parse("2026-08-02T00:00:00Z")
				))
		);
		userRepositoryAccessRepository.save(new UserRepositoryAccess(owner, created));
		return created;
	}

	private RequestPostProcessor oauthUser(
			GithubUser githubUser
	) {
		return oauth2Login().attributes(attributes -> {
			attributes.put("id", githubUser.getGithubId());
			attributes.put("login", githubUser.getLogin());
		});
	}

	private String validRequest(long repositoryId, String title) {
		return """
				{
				  "title": "%s",
				  "evaluationPurpose": "TECH_INTERVIEW",
				  "targetLevel": "JUNIOR",
				  "evaluationAreas": ["BACKEND", "ARCHITECTURE"],
				  "repositories": [%s]
				}
				""".formatted(title, repositoryJson(repositoryId, true));
	}

	private String requestWithRepositories(String repositories) {
		return """
				{
				  "title": "검증 포트폴리오",
				  "evaluationPurpose": "TECH_INTERVIEW",
				  "evaluationAreas": ["BACKEND"],
				  "repositories": %s
				}
				""".formatted(repositories);
	}

	private String updateRequest(long repositoryId, long version) {
		return """
				{
				  "version": %d,
				  "title": "수정된 포트폴리오",
				  "evaluationPurpose": "JOB_APPLICATION",
				  "targetLevel": "MID",
				  "evaluationAreas": ["BACKEND"],
				  "repositories": [%s]
				}
				""".formatted(version, repositoryJson(repositoryId, true));
	}

	private String repositoryJson(long repositoryId, boolean primary) {
		return """
				{
				  "repositoryId": %d,
				  "contributionDescription": "핵심 API와 데이터 모델을 구현했습니다.",
				  "roleSummary": "메인 백엔드 개발자",
				  "roles": [
				    {"roleType": "BACKEND", "participationLevel": "LEAD", "primary": %s}
				  ]
				}
				""".formatted(repositoryId, primary);
	}

	private String addRepositoryRequest(long repositoryId, long version) {
		return """
				{
				  "version": %d,
				  "repositoryId": %d,
				  "contributionDescription": "선택한 저장소의 핵심 기능을 구현했습니다.",
				  "roleSummary": "메인 백엔드 개발자",
				  "roles": [
				    {"roleType": "BACKEND", "participationLevel": "LEAD", "primary": true}
				  ]
				}
				""".formatted(version, repositoryId);
	}

	private String updateRepositoryEntryRequest(long version) {
		return """
				{
				  "version": %d,
				  "contributionDescription": "검색 화면과 상태 관리를 구현했습니다.",
				  "roleSummary": "프런트엔드 핵심 기능 담당",
				  "roles": [
				    {"roleType": "FRONTEND", "participationLevel": "CORE_CONTRIBUTOR", "primary": true}
				  ]
				}
				""".formatted(version);
	}
}

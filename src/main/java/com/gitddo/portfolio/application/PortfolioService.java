package com.gitddo.portfolio.application;

import com.gitddo.github.domain.GithubRepository;
import com.gitddo.github.domain.GithubRepositoryRepository;
import com.gitddo.github.domain.UserRepositoryAccessRepository;
import com.gitddo.member.domain.GithubUser;
import com.gitddo.member.domain.GithubUserRepository;
import com.gitddo.portfolio.domain.Portfolio;
import com.gitddo.portfolio.domain.PortfolioRepository;
import com.gitddo.portfolio.domain.PortfolioRepositorySelection;
import com.gitddo.portfolio.domain.RoleSelection;
import com.gitddo.portfolio.presentation.CreatePortfolioRequest;
import com.gitddo.portfolio.presentation.PortfolioRepositoryRequest;
import com.gitddo.portfolio.presentation.PortfolioResponse;
import com.gitddo.portfolio.presentation.UpdatePortfolioRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;
	private final GithubUserRepository githubUserRepository;
	private final GithubRepositoryRepository githubRepositoryRepository;
	private final UserRepositoryAccessRepository userRepositoryAccessRepository;

	public PortfolioService(
			PortfolioRepository portfolioRepository,
			GithubUserRepository githubUserRepository,
			GithubRepositoryRepository githubRepositoryRepository,
			UserRepositoryAccessRepository userRepositoryAccessRepository
	) {
		this.portfolioRepository = portfolioRepository;
		this.githubUserRepository = githubUserRepository;
		this.githubRepositoryRepository = githubRepositoryRepository;
		this.userRepositoryAccessRepository = userRepositoryAccessRepository;
	}

	@Transactional
	public PortfolioResponse create(Long githubUserId, CreatePortfolioRequest request) {
		GithubUser owner = findGithubUser(githubUserId);
		Portfolio portfolio = new Portfolio(
				owner,
				request.title(),
				request.evaluationPurpose(),
				request.targetLevel(),
				request.evaluationAreas(),
				resolveRepositories(githubUserId, request.repositories())
		);
		return PortfolioResponse.from(portfolioRepository.save(portfolio));
	}

	@Transactional(readOnly = true)
	public List<PortfolioResponse> getAll(Long githubUserId) {
		return portfolioRepository
				.findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(githubUserId)
				.stream()
				.map(PortfolioResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public PortfolioResponse get(Long githubUserId, Long portfolioId) {
		return PortfolioResponse.from(findOwnedPortfolio(githubUserId, portfolioId));
	}

	@Transactional
	public PortfolioResponse update(
			Long githubUserId,
			Long portfolioId,
			UpdatePortfolioRequest request
	) {
		Portfolio portfolio = findOwnedPortfolio(githubUserId, portfolioId);
		if (portfolio.getVersion() != request.version()) {
			throw new PortfolioVersionConflictException();
		}
		portfolio.update(
				request.title(),
				request.evaluationPurpose(),
				request.targetLevel(),
				request.evaluationAreas(),
				resolveRepositories(githubUserId, request.repositories())
		);
		return PortfolioResponse.from(portfolioRepository.saveAndFlush(portfolio));
	}

	@Transactional
	public void delete(Long githubUserId, Long portfolioId) {
		findOwnedPortfolio(githubUserId, portfolioId).delete();
	}

	private List<PortfolioRepositorySelection> resolveRepositories(
			Long githubUserId,
			List<PortfolioRepositoryRequest> requests
	) {
		return requests.stream()
				.map(request -> resolveRepository(githubUserId, request))
				.toList();
	}

	private PortfolioRepositorySelection resolveRepository(
			Long githubUserId,
			PortfolioRepositoryRequest request
	) {
		if (!userRepositoryAccessRepository
				.existsByGithubUserGithubIdAndGithubRepositoryGithubIdAndActiveTrue(
						githubUserId,
						request.repositoryId()
				)) {
			throw new IllegalArgumentException(
					"현재 사용자가 접근할 수 없는 저장소입니다: " + request.repositoryId()
			);
		}
		GithubRepository repository = githubRepositoryRepository
				.findByGithubId(request.repositoryId())
				.orElseThrow(() -> new IllegalArgumentException(
						"동기화되지 않은 저장소입니다: " + request.repositoryId()
				));

		List<RoleSelection> roles = request.roles().stream()
				.map(role -> new RoleSelection(
						role.roleType(),
						role.participationLevel(),
						role.primary()
				))
				.toList();
		return new PortfolioRepositorySelection(
				repository,
				request.contributionDescription(),
				request.roleSummary(),
				roles
		);
	}

	private GithubUser findGithubUser(Long githubUserId) {
		return githubUserRepository.findByGithubId(githubUserId)
				.orElseThrow(() -> new IllegalStateException("로그인 사용자 정보가 DB에 없습니다."));
	}

	private Portfolio findOwnedPortfolio(Long githubUserId, Long portfolioId) {
		return portfolioRepository
				.findByIdAndOwnerGithubIdAndDeletedAtIsNull(portfolioId, githubUserId)
				.orElseThrow(PortfolioNotFoundException::new);
	}
}

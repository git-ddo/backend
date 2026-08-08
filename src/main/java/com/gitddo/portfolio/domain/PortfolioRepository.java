package com.gitddo.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

	Optional<Portfolio> findByIdAndOwnerGithubIdAndDeletedAtIsNull(
			Long id,
			Long ownerGithubId
	);

	List<Portfolio> findByOwnerGithubIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
			Long ownerGithubId
	);
}

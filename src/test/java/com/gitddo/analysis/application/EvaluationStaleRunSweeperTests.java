package com.gitddo.analysis.application;

import com.gitddo.analysis.domain.EvaluationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationStaleRunSweeperTests {

	@Mock
	private EvaluationService evaluationService;

	@Test
	void expiresRunsOlderThanConfiguredWindow() {
		when(evaluationService.expireStale(any(), any())).thenReturn(List.of());
		EvaluationStaleRunSweeper sweeper = new EvaluationStaleRunSweeper(
				evaluationService,
				Duration.ofMinutes(20)
		);

		Instant before = Instant.now().minus(Duration.ofMinutes(20));
		sweeper.expireStaleRuns();
		Instant after = Instant.now().minus(Duration.ofMinutes(20));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<EvaluationStatus>> statuses =
				ArgumentCaptor.forClass(Collection.class);
		ArgumentCaptor<Instant> deadline = ArgumentCaptor.forClass(Instant.class);
		verify(evaluationService).expireStale(statuses.capture(), deadline.capture());

		assertThat(statuses.getValue()).isEqualTo(EnumSet.of(
				EvaluationStatus.REQUESTED,
				EvaluationStatus.COLLECTING,
				EvaluationStatus.EVIDENCE_READY,
				EvaluationStatus.ANALYZING
		));
		assertThat(deadline.getValue()).isBetween(before, after);
		assertThat(Set.copyOf(statuses.getValue())).doesNotContain(
				EvaluationStatus.SUCCEEDED,
				EvaluationStatus.FAILED
		);
	}
}

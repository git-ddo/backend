package com.gitddo.analysis.application;

import com.gitddo.analysis.contract.AnalysisDepth;
import com.gitddo.analysis.domain.EvidenceKind;
import com.gitddo.analysis.domain.EvidenceType;
import com.gitddo.analysis.domain.P0EvidenceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPathResolverTests {

	private final CurrentPathResolver resolver = new CurrentPathResolver();

	@Test
	void keepsExactCurrentPath() {
		CurrentTreeIndex tree = tree("blob\tsrc/main/java/com/qeat/domain/User.java");

		CurrentPathResolver.ResolvedPath resolved = resolver.resolve(
				"src/main/java/com/qeat/domain/User.java",
				tree
		);

		assertThat(resolved.found()).isTrue();
		assertThat(resolved.currentPath()).isEqualTo("src/main/java/com/qeat/domain/User.java");
		assertThat(resolved.remapped()).isFalse();
	}

	@Test
	void remapsPackageAndServiceCaseToCurrentTree() {
		CurrentTreeIndex tree = tree("blob\tsrc/main/java/com/qeat/service/TableService.java");

		CurrentPathResolver.ResolvedPath resolved = resolver.resolve(
				"src/main/java/com/example/demo/Service/TableService.java",
				tree
		);

		assertThat(resolved.found()).isTrue();
		assertThat(resolved.currentPath()).isEqualTo("src/main/java/com/qeat/service/TableService.java");
		assertThat(resolved.remapped()).isTrue();
	}

	@Test
	void leavesDeletedPathUnresolved() {
		CurrentTreeIndex tree = tree("blob\tsrc/main/java/com/qeat/service/TableService.java");

		CurrentPathResolver.ResolvedPath resolved = resolver.resolve(
				"src/main/java/com/example/demo/controller/SejongController.java",
				tree
		);

		assertThat(resolved.found()).isFalse();
	}

	private CurrentTreeIndex tree(String content) {
		return CurrentTreeIndex.from(
				List.of(new P0EvidenceSnapshot.Evidence(
						"ev_tree",
						EvidenceType.GITHUB_STATIC,
						EvidenceKind.FILE_TREE_SUMMARY,
						AnalysisDepth.P0,
						"123",
						"commit-sha",
						null,
						null,
						null,
						null,
						null,
						content,
						"hash",
						false,
						List.of()
				)),
				"123"
		);
	}
}

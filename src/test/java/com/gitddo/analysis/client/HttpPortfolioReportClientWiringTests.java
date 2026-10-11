package com.gitddo.analysis.client;

import com.gitddo.analysis.config.AiClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gitddo.ai.mode 값에 따라 AI HTTP 클라이언트가 Spring 컨테이너 안에서
 * 정상적으로 만들어지는지 검증한다.
 * 기본 설정(mock)으로 뜨는 전체 앱 테스트로는 이 경로가 검증되지 않는다.
 */
class HttpPortfolioReportClientWiringTests {

	// 실제 앱(SpringApplication)은 "5s" 같은 설정 문자열을 Duration으로 바꾸는 변환기를 자동 등록하지만,
	// ApplicationContextRunner는 등록하지 않으므로 같은 조건을 맞춰 준다.
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withInitializer(context -> context.getBeanFactory()
					.setConversionService(ApplicationConversionService.getSharedInstance()))
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
			.withUserConfiguration(AiClientConfig.class, HttpPortfolioReportClient.class);

	@Test
	void createsHttpClientWhenModeIsHttp() {
		contextRunner
				.withPropertyValues(
						"gitddo.ai.mode=http",
						"gitddo.ai.base-url=http://localhost:9999"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(HttpPortfolioReportClient.class);
				});
	}

	@Test
	void failsWithClearMessageWhenBaseUrlIsMissing() {
		contextRunner
				.withPropertyValues("gitddo.ai.mode=http")
				.run(context -> assertThat(context)
						.hasFailed()
						.getFailure()
						.rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("gitddo.ai.base-url"));
	}

	@Test
	void doesNotCreateHttpClientWhenModeIsMock() {
		contextRunner
				.withPropertyValues("gitddo.ai.mode=mock")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(HttpPortfolioReportClient.class);
				});
	}
}

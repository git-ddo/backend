package com.gitddo.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "Gitddo API",
				version = "v1",
				description = "GitHub 저장소 기반 포트폴리오 분석 서비스 API"
		)
)
@SecurityScheme(
		name = "sessionAuth",
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.COOKIE,
		paramName = "JSESSIONID",
		description = "GitHub OAuth 로그인 후 발급되는 HttpOnly 세션 쿠키"
)
public class OpenApiConfig {
}

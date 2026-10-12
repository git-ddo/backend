package com.gitddo.auth.config;

import com.gitddo.auth.application.GithubOAuth2UserService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	private final GithubOAuth2UserService githubOAuth2UserService;
	private final String frontendUrl;

	public SecurityConfig(
			GithubOAuth2UserService githubOAuth2UserService,
			@Value("${gitddo.frontend-url}") String frontendUrl
	) {
		this.githubOAuth2UserService = githubOAuth2UserService;
		this.frontendUrl = stripTrailingSlash(frontendUrl);
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository =
				CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setHeaderName("X-XSRF-TOKEN");
		return repository;
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			CookieCsrfTokenRepository csrfTokenRepository
	) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/info",
								"/oauth2/**",
								"/login/**",
								"/v3/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/error"
						).permitAll()
						.anyRequest().authenticated()
				)
				.oauth2Login(oauth -> oauth
						.userInfoEndpoint(userInfo -> userInfo
								.userService(githubOAuth2UserService)
						)
						.defaultSuccessUrl(frontendUrl + "/dashboard", true)
				)
				.logout(logout -> logout
						.logoutUrl("/api/v1/logout")
						.logoutSuccessUrl(frontendUrl + "/")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID", "XSRF-TOKEN")
				)
				// API 호출은 로그인 여부만 401로 알려주고, 로그인 페이지 이동은 프론트가 판단한다.
				.exceptionHandling(exceptions -> exceptions
						.defaultAuthenticationEntryPointFor(
								new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
								PathPatternRequestMatcher.pathPattern("/api/**")
						)
				);

		return http.build();
	}

	/**
	 * 브라우저가 프론트 출처의 JS에 백엔드 응답을 읽도록 허용할 명단.
	 * 세션 쿠키를 함께 보내야 하므로 allowCredentials=true 이고, 이 경우 출처는 와일드카드(*)를 쓸 수 없다.
	 */
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(frontendUrl));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}

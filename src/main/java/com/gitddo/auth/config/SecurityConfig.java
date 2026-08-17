package com.gitddo.auth.config;

import com.gitddo.auth.application.GithubOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

	private final GithubOAuth2UserService githubOAuth2UserService;

	public SecurityConfig(GithubOAuth2UserService githubOAuth2UserService) {
		this.githubOAuth2UserService = githubOAuth2UserService;
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
						.defaultSuccessUrl("/api/v1/me", true)
				)
				.logout(logout -> logout
						.logoutUrl("/api/v1/logout")
						.logoutSuccessUrl("/actuator/health")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID", "XSRF-TOKEN")
				);

		return http.build();
	}
}

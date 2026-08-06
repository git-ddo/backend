package com.gitddo.auth.config;

import com.gitddo.auth.application.GithubOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	private final GithubOAuth2UserService githubOAuth2UserService;

	public SecurityConfig(GithubOAuth2UserService githubOAuth2UserService) {
		this.githubOAuth2UserService = githubOAuth2UserService;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
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
						.deleteCookies("JSESSIONID")
				);

		return http.build();
	}
}

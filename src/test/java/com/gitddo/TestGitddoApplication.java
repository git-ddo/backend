package com.gitddo;

import org.springframework.boot.SpringApplication;

public class TestGitddoApplication {

	public static void main(String[] args) {
		SpringApplication.from(GitddoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

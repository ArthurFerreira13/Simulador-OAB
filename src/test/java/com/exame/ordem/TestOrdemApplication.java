package com.exame.ordem;

import org.springframework.boot.SpringApplication;

public class TestOrdemApplication {

	public static void main(String[] args) {
		SpringApplication.from(OrdemApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

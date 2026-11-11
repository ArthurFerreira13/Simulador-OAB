package com.exame.ordem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrdemApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrdemApplication.class, args);
	}

}

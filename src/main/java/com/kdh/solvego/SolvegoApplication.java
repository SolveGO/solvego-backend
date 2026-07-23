package com.kdh.solvego;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class SolvegoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SolvegoApplication.class, args);
	}

}

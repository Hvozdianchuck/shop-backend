package com.shop.supportservice;

import org.springframework.boot.SpringApplication;

public class TestSupportServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SupportServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

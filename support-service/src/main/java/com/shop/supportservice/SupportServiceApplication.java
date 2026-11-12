package com.shop.supportservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupportServiceApplication.class, args);
	}

}

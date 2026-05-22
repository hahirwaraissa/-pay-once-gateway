package com.igirepay.pay_once_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PayOnceGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayOnceGatewayApplication.class, args);
	}

}


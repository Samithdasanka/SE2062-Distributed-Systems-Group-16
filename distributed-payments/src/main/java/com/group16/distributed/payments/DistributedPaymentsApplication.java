package com.group16.distributed.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DistributedPaymentsApplication {
	public static void main(String[] args) {
		SpringApplication.run(DistributedPaymentsApplication.class, args);
	}
}
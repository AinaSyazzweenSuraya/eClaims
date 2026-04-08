package com.eclaims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EClaimsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EClaimsApplication.class, args);
    }
}

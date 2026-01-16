package com.ubs.orkestra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class OrkestraV2ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrkestraV2ApiApplication.class, args);
    }
}
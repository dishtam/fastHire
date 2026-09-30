package com.fasthire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FastHireApplication {
    public static void main(String[] args) {
        SpringApplication.run(FastHireApplication.class, args);
    }
}

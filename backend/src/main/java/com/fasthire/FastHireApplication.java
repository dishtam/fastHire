package com.fasthire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FastHireApplication {
    public static void main(String[] args) {
        // Legacy zone ids (e.g. Asia/Calcutta) are rejected by newer Postgres; timestamps are timestamptz anyway.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(FastHireApplication.class, args);
    }
}

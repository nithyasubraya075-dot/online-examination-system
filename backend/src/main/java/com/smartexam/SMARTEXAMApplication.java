package com.smartexam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SMARTEXAMApplication {
    public static void main(String[] args) {
        SpringApplication.run(SMARTEXAMApplication.class, args);
    }
}

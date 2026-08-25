package com.readingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ReadingAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReadingAgentApplication.class, args);
    }
}

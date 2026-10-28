package com.dailyreading;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dailyreading.mapper")
public class DailyReadingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DailyReadingApplication.class, args);
    }
}

package com.electricwealth.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ElectricWealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElectricWealthApplication.class, args);
    }
}

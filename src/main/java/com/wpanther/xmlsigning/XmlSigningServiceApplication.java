package com.wpanther.xmlsigning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Main Spring Boot application class for XML Signing Service
 */
@SpringBootApplication
@EnableFeignClients
public class XmlSigningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(XmlSigningServiceApplication.class, args);
    }
}

package com.oficina.billing.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class TcOficinaBillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TcOficinaBillingApplication.class, args);
    }
}

package com.example.ssomock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Mock SSO portálu: drží identitu operátora a vybraného zákazníka, CRM (stará aj nová appka) ich číta cez REST. */
@SpringBootApplication
public class SsoMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoMockApplication.class, args);
    }
}

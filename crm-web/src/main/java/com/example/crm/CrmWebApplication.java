package com.example.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Nová appka so zmigrovaným portletom blacklist. Dedí zo {@link SpringBootServletInitializer}, aby sa ten istý WAR
 * dal nasadiť aj do externého servlet kontajnera (podklady riešia nasadenie na aplikačný server).
 */
@SpringBootApplication
public class CrmWebApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(CrmWebApplication.class, args);
    }
}

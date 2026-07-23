package com.ebank.admin;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Spring Boot Admin server for the eBank microservices platform.
 *
 * <p>Instances are discovered from a static {@code SimpleDiscoveryClient} list
 * (see {@code application.yml} → {@code spring.cloud.discovery.client.simple.instances}),
 * so the monitored services (gateway, auth, accounts, transactions, chatbot) need
 * no client dependency — the Admin server simply polls their already-exposed
 * {@code /actuator/**} endpoints.
 */
@SpringBootApplication
@EnableAdminServer
@EnableDiscoveryClient
public class AdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServerApplication.class, args);
    }
}

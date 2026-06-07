package com.ebank.transactions.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                title = "Transactions API",
                contact = @Contact(
                        name = "Sam",
                        url = "https://github.com/Samlogy"
                ),
                version = "1"
        ),
        servers = {
                @Server(
                        url = "http://localhost:8083/api/transactions",
                        description = "Transactions API - Local"
                )
        }
)
public class SwaggerConfig {
}

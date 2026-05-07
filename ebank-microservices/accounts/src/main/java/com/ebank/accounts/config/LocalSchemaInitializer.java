package com.ebank.accounts.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

@Configuration
@Profile("local")
public class LocalSchemaInitializer {

    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1.0.0__init.sql")));
        return initializer;
    }
}

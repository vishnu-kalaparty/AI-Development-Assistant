package com.aidevassistant.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(
            @Value("${neo4j.uri:bolt://localhost:7687}") String uri,
            @Value("${neo4j.username:neo4j}") String username,
            @Value("${neo4j.password:password}") String password
    ) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}

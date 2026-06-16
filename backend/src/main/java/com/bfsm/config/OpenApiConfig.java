package com.bfsm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("BFSM API")
                .version("0.0.1")
                .description("BFSM backend API"));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder().group("01-auth").pathsToMatch("/api/auth/**").build();
    }

    @Bean
    public GroupedOpenApi appointmentApi() {
        return GroupedOpenApi.builder().group("02-appointment").pathsToMatch("/api/appointments/**", "/api/scheduling/**").build();
    }

    @Bean
    public GroupedOpenApi masterApi() {
        return GroupedOpenApi.builder().group("03-master-data").pathsToMatch("/api/master/**", "/api/admin/**").build();
    }

    @Bean
    public GroupedOpenApi statsApi() {
        return GroupedOpenApi.builder().group("04-stats").pathsToMatch("/api/stats/**").build();
    }

    @Bean
    public GroupedOpenApi shopApi() {
        return GroupedOpenApi.builder().group("05-shop").pathsToMatch("/api/products/**", "/api/interaction/**").build();
    }

    @Bean
    public GroupedOpenApi chatApi() {
        return GroupedOpenApi.builder().group("06-chat").pathsToMatch("/api/chat/**", "/ws/**").build();
    }
}


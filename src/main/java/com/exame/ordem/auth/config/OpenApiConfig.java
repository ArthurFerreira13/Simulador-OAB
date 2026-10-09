package com.exame.ordem.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura o Swagger/OpenAPI:
 *  - Declara o esquema de segurança Bearer JWT (faz o botão "Authorize" aparecer)
 *  - Adiciona info básica da API
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Simulador-OAB API")
                        .version("v1")
                        .description("Plataforma de preparação para o Exame de Ordem"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Cole aqui apenas o access token (sem 'Bearer ')")))
                                        .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }
}
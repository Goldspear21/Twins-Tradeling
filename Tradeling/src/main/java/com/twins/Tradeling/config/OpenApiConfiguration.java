package com.twins.Tradeling.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI stockOpenAPI() {
        return new OpenAPI().info(new Info().title("Tradeling Stock Interface").version("v1")
                .description("Stock interface for Abu Dhabi, Dubai and Sharjah. Currency: AED. Stable warehouse enums and explicit JSON DTOs. Start with /api/v1/context. Sales and completed replenishments atomically change stock; plans NEVER execute inventory actions. Errors use timestamp, status, code, message and fieldErrors. HTTP 400: invalid input; 404: missing record; 409: duplicate reference, insufficient stock or conflicting update. No authentication is configured: development/trusted use only."));
    }
}

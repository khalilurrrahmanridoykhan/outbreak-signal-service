package io.github.khalilurrrahmanridoykhan.outbreaksignal.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documents the bearer-JWT requirement in the generated OpenAPI/Swagger UI; see {@code docs/keycloak.md} for getting a real token to try it with. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI outbreakSignalServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Outbreak Signal Service")
                        .description("Pulls weekly surveillance data from DHIS2, flags statistical aberrations, "
                                + "and publishes alerts for review. An alert is a statistical signal, not a "
                                + "confirmed outbreak — see ETHICS.md.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .name(BEARER_AUTH)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("A JWT from the configured issuer (the optional local Keycloak "
                                + "by default — see docs/keycloak.md) with a realm_access.roles claim "
                                + "naming VIEWER, ANALYST or ADMIN.")));
    }
}

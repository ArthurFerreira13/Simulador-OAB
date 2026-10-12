package com.exame.ordem.auth.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;

/**
 * Converte a claim {@code role} do JWT em {@code GrantedAuthority}.
 * Prefixa com {@code ROLE_} para que {@code hasRole("ADMIN")} funcione.
 */
public final class JwtRoleConverter {

    private JwtRoleConverter() {}

    public static Converter<Jwt, AbstractAuthenticationToken> build() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });

        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
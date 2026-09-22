package com.exame.ordem.auth.internal;

import com.exame.ordem.auth.dto.TokenResponse;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private static final String ISSUER = "simulador-oab";

    private final JwtEncoder encoder;
    private final long expirationMinutes;

    JwtService(JwtEncoder encoder,
               @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.encoder = encoder;
        this.expirationMinutes = expirationMinutes;
    }

    public TokenResponse gerar(UsuarioResumo usuario) {
        Instant agora = Instant.now();
        Instant expiracao = agora.plus(expirationMinutes, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(agora)
                .expiresAt(expiracao)
                .subject(usuario.id().toString())
                .claim("email", usuario.email())
                .claim("nome", usuario.nome())
                .claim("role", "USER")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(token, "Bearer", expirationMinutes * 60);
    }
}
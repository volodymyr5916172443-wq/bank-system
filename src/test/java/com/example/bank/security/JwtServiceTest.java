package com.example.bank.security;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import com.example.bank.config.JwtConfig;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final String OTHER_SECRET = "another-secret-another-secret-1234567";

    private final JwtConfig config = new JwtConfig();

    @Test
    void tokenContainsEmailAndExpiry() {
        JwtService service = new JwtService(config.jwtEncoder(SECRET), 60);

        Jwt jwt = config.jwtDecoder(SECRET).decode(service.generateToken("anna@test.com"));

        assertThat(jwt.getSubject()).isEqualTo("anna@test.com");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(59 * 60));
        assertThat(service.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = new JwtService(config.jwtEncoder(OTHER_SECRET), 60).generateToken("hacker@test.com");

        assertThatThrownBy(() -> config.jwtDecoder(SECRET).decode(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("anna@test.com").issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(300)).build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String expired = config.jwtEncoder(SECRET)
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        assertThatThrownBy(() -> config.jwtDecoder(SECRET).decode(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tooShortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> config.jwtEncoder("short"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtDecoder(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
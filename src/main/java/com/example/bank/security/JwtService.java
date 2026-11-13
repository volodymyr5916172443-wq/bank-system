package com.example.bank.security;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service 
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration expiration;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.expiration-minutes:60}")long expirationMinutes){
        this.encoder = encoder;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }
    public String generateToken(String email){
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(email).issuedAt(now).expiresAt(now.plus(expiration)).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
    public long getExpirationSeconds(){
        return expiration.toSeconds();
    }
}

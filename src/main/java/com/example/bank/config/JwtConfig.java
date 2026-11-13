package com.example.bank.config;


import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration 
public class JwtConfig {
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.jwt.secret}")String secret){
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(secret)));
    }
    @Bean 
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}")String secret){
        return NimbusJwtDecoder.withSecretKey(secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }
    private static SecretKey secretKey(String secret){
        byte[] bytes = secret == null ? new byte[0]:secret.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<MIN_SECRET_BYTES){
            throw new IllegalStateException("app.jwt.secret must be at least this long "+MIN_SECRET_BYTES+" characters");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}

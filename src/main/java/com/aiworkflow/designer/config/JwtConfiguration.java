package com.aiworkflow.designer.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Configuration
public class JwtConfiguration {

    private static final String DEV_SECRET = "dev-only-secret-key-change-in-production-32chars";
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    private final Environment environment;

    public JwtConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fails fast on unsafe signing keys. Outside the local/test profiles a
     * strong JWT_SECRET is mandatory — a known or missing HS256 key lets
     * anyone forge tokens, including admin-role tokens.
     */
    @PostConstruct
    void validateSecret() {
        if (isLocalOrTest()) {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                jwtSecret = DEV_SECRET;
            }
            return;
        }
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Refusing to start without a signing secret "
                            + "outside the local/test profiles.");
        }
        if (DEV_SECRET.equals(jwtSecret) || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is weak (well-known value or shorter than " + MIN_SECRET_BYTES
                            + " bytes). Set a strong random secret before deploying.");
        }
    }

    private boolean isLocalOrTest() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return Arrays.stream(profiles).anyMatch(p -> p.equals("local") || p.equals("test"));
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}

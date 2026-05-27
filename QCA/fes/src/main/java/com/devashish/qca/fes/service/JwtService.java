package com.devashish.qca.fes.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtService(
            @Value("${qca.security.jwt-signing-key}") String signingKey,
            @Value("${qca.security.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${qca.security.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String createAccessToken(String subject) {
        return buildToken(subject, ACCESS_TOKEN_TYPE, accessTokenTtlSeconds);
    }

    public String createRefreshToken(String subject) {
        return buildToken(subject, REFRESH_TOKEN_TYPE, refreshTokenTtlSeconds);
    }

    public Jws<Claims> parseAccessToken(String token) {
        Jws<Claims> claims = parse(token);
        requireTokenType(claims, ACCESS_TOKEN_TYPE);
        return claims;
    }

    public Jws<Claims> parseRefreshToken(String token) {
        Jws<Claims> claims = parse(token);
        requireTokenType(claims, REFRESH_TOKEN_TYPE);
        return claims;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    private String buildToken(String subject, String tokenType, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim(ROLE_CLAIM, "FES_OPERATOR")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }

    private void requireTokenType(Jws<Claims> claims, String expectedTokenType) {
        String actualTokenType = claims.getPayload().get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedTokenType.equals(actualTokenType)) {
            throw new io.jsonwebtoken.JwtException("invalid token type");
        }
    }
}

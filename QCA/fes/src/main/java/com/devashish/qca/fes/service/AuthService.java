package com.devashish.qca.fes.service;

import com.devashish.qca.fes.dto.AuthTokenResponse;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final JwtService jwtService;
    private final String demoUsername;
    private final byte[] demoPasswordBytes;

    public AuthService(
            JwtService jwtService,
            @Value("${qca.security.demo-username}") String demoUsername,
            @Value("${qca.security.demo-password}") String demoPassword) {
        this.jwtService = jwtService;
        this.demoUsername = demoUsername;
        this.demoPasswordBytes = demoPassword.getBytes(StandardCharsets.UTF_8);
    }

    public AuthTokenResponse login(String username, String password) {
        if (!demoUsername.equals(username) || !matchesPassword(password)) {
            log.warn("login rejected username={}", username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        log.info("login issued username={}", demoUsername);
        return issueTokens(demoUsername);
    }

    public AuthTokenResponse refresh(String refreshToken) {
        try {
            String subject = jwtService.parseRefreshToken(refreshToken).getPayload().getSubject();
            log.info("refresh issued username={}", subject);
            return issueTokens(subject);
        } catch (JwtException exception) {
            log.warn("refresh rejected");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }
    }

    private AuthTokenResponse issueTokens(String subject) {
        return new AuthTokenResponse(
                jwtService.createAccessToken(subject),
                jwtService.createRefreshToken(subject),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                jwtService.getRefreshTokenTtlSeconds());
    }

    private boolean matchesPassword(String password) {
        if (password == null) {
            return false;
        }

        return MessageDigest.isEqual(demoPasswordBytes, password.getBytes(StandardCharsets.UTF_8));
    }
}

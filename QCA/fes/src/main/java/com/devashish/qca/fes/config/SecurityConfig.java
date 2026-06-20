package com.devashish.qca.fes.config;

import com.devashish.qca.fes.service.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilter,
            @Value("${qca.security.auth-provider}") String authProvider) throws Exception {
        HttpSecurity security = http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/create-scan", "/api/start-scan").authenticated()
                        .anyRequest().permitAll());

        if ("cognito".equalsIgnoreCase(authProvider)) {
            security.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(cognitoAuthenticationConverter())));
        } else {
            JwtAuthenticationFilter filter = jwtAuthenticationFilter.getIfAvailable();
            if (filter != null) {
                security.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
            }
        }

        return security.build();
    }

    @Bean
    public JwtDecoder cognitoJwtDecoder(
            @Value("${qca.security.auth-provider}") String authProvider,
            @Value("${qca.security.cognito.issuer-uri}") String issuerUri,
            @Value("${qca.security.cognito.client-id}") String clientId) {
        if (!"cognito".equalsIgnoreCase(authProvider)) {
            return token -> {
                throw new IllegalStateException("Cognito JWT decoder is disabled");
            };
        }
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("qca.security.cognito.issuer-uri is required when auth-provider=cognito");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("qca.security.cognito.client-id is required when auth-provider=cognito");
        }

        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> cognitoValidator = jwt -> {
            String tokenUse = jwt.getClaimAsString("token_use");
            String tokenClientId = jwt.getClaimAsString("client_id");
            boolean accessToken = "access".equals(tokenUse);
            boolean expectedClient = clientId.equals(tokenClientId) || jwt.getAudience().contains(clientId);
            if (accessToken && expectedClient) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Cognito access token must be issued for the configured app client",
                    null);
            return OAuth2TokenValidatorResult.failure(error);
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, cognitoValidator));
        return decoder;
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> cognitoAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                jwt.getSubject());
    }
}

package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.db.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebFluxSecurity
class ContentSecurityPolicyConfiguration {
    @Autowired
    TokenRepository tokenRepository;

    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        return tokenRepository.findItemByToken(token)
                .switchIfEmpty(Mono.error(new BadOpaqueTokenException("unknown token")))
                .map(t -> new DefaultOAuth2AuthenticatedPrincipal(t.user, Map.of("sub", t.user, "src", "oauth"),
                                                                  Set.of()));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.csrf().disable();
        http.oauth2ResourceServer().opaqueToken().introspector(this::introspect);
        return http.build();
    }
}

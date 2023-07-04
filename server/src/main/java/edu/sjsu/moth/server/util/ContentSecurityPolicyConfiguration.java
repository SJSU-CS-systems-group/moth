package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.db.TokenRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Set;

/**
 * wire up the security filters
 */
@CommonsLog
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

    /**
     * i thought we could have multiple chains, but it looks like it picks only
     * one if there are multiple, so this is the chain we will use for all the
     * security processing.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.addFilterAt(new HttpSignatureAuthenticationProcessingFilter(this::authenticationManager),
                         SecurityWebFiltersOrder.AUTHENTICATION);
        http.oauth2ResourceServer(
                rsSpec -> rsSpec.opaqueToken(opaqueTokenSpec -> opaqueTokenSpec.introspector(this::introspect)));
        return http.build();
    }

    private Mono<Authentication> authenticationManager(Authentication authentication) {
        log.info("Authenticating " + ((UserPrincipal) authentication.getPrincipal()).getName());
        return Mono.just(authentication);
    }

}

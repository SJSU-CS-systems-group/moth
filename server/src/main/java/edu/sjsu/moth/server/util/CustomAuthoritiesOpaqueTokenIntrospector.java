package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.db.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class CustomAuthoritiesOpaqueTokenIntrospector implements ReactiveOpaqueTokenIntrospector {
    @Autowired
    TokenRepository tokenRepository;

    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        return tokenRepository.findItemByToken(token)
                .map(t -> new DefaultOAuth2AuthenticatedPrincipal(t.user, Map.of("sub", t.user), Set.of()));
    }

    private Collection<GrantedAuthority> extractAuthorities(OAuth2AuthenticatedPrincipal principal) {
        return Set.of();
    }
}

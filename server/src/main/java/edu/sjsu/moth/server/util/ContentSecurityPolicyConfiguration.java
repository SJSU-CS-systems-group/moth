package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.db.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebFluxSecurity
public class ContentSecurityPolicyConfiguration {
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
        http.addFilterBefore(localeChangeFilter(), SecurityWebFiltersOrder.FIRST);
        return http.build();
    }

    public LocaleChangeFilter localeChangeFilter() {
        return new LocaleChangeFilter();
    }

    public static class LocaleChangeFilter implements WebFilter {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            String acceptLanguage = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE);
            // to improve this code, i would say you could technically process the data grabbed above by "Q" value to
            // switch to the most 'preferred' value by the User.
            Locale resolvedLocale = (acceptLanguage != null) ? Locale.forLanguageTag(
                    acceptLanguage) : Locale.getDefault();
            LocaleContextHolder.setLocale(resolvedLocale);
            return chain.filter(exchange);
        }
    }
}

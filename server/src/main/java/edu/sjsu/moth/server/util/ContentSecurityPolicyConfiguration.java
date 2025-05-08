package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.db.Token;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.filter.HttpSignatureWebFilter;
import edu.sjsu.moth.server.service.HttpSignatureService;
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

    @Autowired
    HttpSignatureService httpSignatureService;

    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        return tokenRepository.findItemByToken(token)
                .switchIfEmpty(Mono.error(new BadOpaqueTokenException("unknown token"))).map(this::sanitizeToken)
                .map(t -> new DefaultOAuth2AuthenticatedPrincipal(t.user, Map.of("sub", t.user, "src", "oauth", "email",
                                                                                 t.email), Set.of()));
    }

    /**
     * this should be temporary while we get everything converted over to use emails. it just
     * makes sure that nothing is null.
     *
     * @param token token to sanitize (changed in place)
     * @return the original token object that has been sanitized
     */
    private Token sanitizeToken(Token token) {
        if (token.token == null) token.token = "";
        if (token.email == null) token.email = "nothing@nothing";
        if (token.appName == null) token.appName = "app";
        if (token.user == null) token.user = "";
        if (token.appWebsite == null) token.appWebsite = "https://nowhere.example.com";
        return token;
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.oauth2ResourceServer(rsSpec -> rsSpec.opaqueToken(otSpec -> otSpec.introspector(this::introspect)));
        http.addFilterBefore(localeChangeFilter(), SecurityWebFiltersOrder.FIRST);
        http.addFilterAfter(httpSignatureWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION);
        return http.build();
    }

    public LocaleChangeFilter localeChangeFilter() {
        return new LocaleChangeFilter();
    }

    @Bean
    public HttpSignatureWebFilter httpSignatureWebFilter() {
        return new HttpSignatureWebFilter(httpSignatureService);
    }

    public static class LocaleChangeFilter implements WebFilter {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            String acceptLanguage = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE);
            // to improve this code, i would say you could technically process the data grabbed above by "Q" value to
            // switch to the most 'preferred' value by the User.
            Locale resolvedLocale =
                    (acceptLanguage != null) ? Locale.forLanguageTag(acceptLanguage) : Locale.getDefault();
            LocaleContextHolder.setLocale(resolvedLocale);
            return chain.filter(exchange);
        }
    }
}

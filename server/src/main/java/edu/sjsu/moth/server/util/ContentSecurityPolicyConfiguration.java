package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.controller.MothController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.text.MessageFormat;

@Configuration
class ContentSecurityPolicyConfiguration {
    private static final String POLICY_DIRECTIVES = MessageFormat.format(
            "base-uri 'none'; default-src 'none'; frame-ancestors 'none'; font-src 'self' {0}; img-src 'self' https: " +
                    "data: blob: {0}; style-src 'self' {0} 'nonce-ZmE1OSpB3aQzc4hfGwKPZw=='; media-src 'self' https: " +
                    "data: {0}; frame-src 'self' https:; manifest-src 'self' {0}; form-action 'self'; connect-src " +
                    "'self' data: blob: {0} wss://{1}; script-src 'self' {0} 'wasm-unsafe-eval'; child-src 'self' " +
                    "blob: {0}; worker-src 'self' blob: {0}",
            MothController.BASE_URL, MothConfiguration.mothConfiguration.getServerName());

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // i don't think we want this security policy, but we did see it used by other
        // server. we should circle back to this
        // http.headers().contentSecurityPolicy(POLICY_DIRECTIVES);
        // since almost all our requests are not coming from a web page, we need to turn
        // off CSRF
        http.csrf().disable();
        return http.build();
    }

}

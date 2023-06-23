package edu.sjsu.moth.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class ContentSecurityPolicyConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers().contentSecurityPolicy(
                "base-uri 'none'; " + "default-src 'none'; " + "frame-ancestors 'none'; " + "font-src 'self' " +
                        "https://samuel.homeofcode.com; " + "img-src 'self' https: data: blob: https://samuel" +
                        ".homeofcode.com; " + "style-src 'self' https://samuel.homeofcode.com " +
                        "'nonce-ZmE1OSpB3aQzc4hfGwKPZw=='; " + "media-src 'self' https: data: https://samuel" +
                        ".homeofcode.com; " + "frame-src 'self' https:; manifest-src 'self' https://samuel.homeofcode" +
                        ".com; " + "form-action 'self'; " + "connect-src 'self' data: blob: https://samuel.homeofcode" +
                        ".com wss://samuel.homeofcode.com; script-src 'self' https://samuel.homeofcode.com " +
                        "'wasm-unsafe-eval'; " + "child-src 'self' blob: https://samuel.homeofcode.com; " + "worker" +
                        "-src 'self' blob: https://samuel.homeofcode.com");
        return http.build();
    }

}

package edu.sjsu.moth.server.filter;

import edu.sjsu.moth.server.service.HttpSignatureService;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@CommonsLog
public class HttpSignatureWebFilter implements WebFilter {

    private final HttpSignatureService httpSignatureService;
    private final List<PathPattern> protectedPatterns;

    public HttpSignatureWebFilter(HttpSignatureService httpSignatureService) {
        this.httpSignatureService = httpSignatureService;
        PathPatternParser parser = new PathPatternParser();
        this.protectedPatterns = List.of(parser.parse("/inbox"), parser.parse("/users/{id}/inbox"));
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        PathContainer requestPath = request.getPath().pathWithinApplication();
        HttpMethod requestMethod = request.getMethod();

        boolean protectedPost =
                requestMethod == HttpMethod.POST && protectedPatterns.stream().anyMatch(p -> p.matches(requestPath));
        if (!protectedPost) {
            return chain.filter(exchange);
        }

        boolean hasSignature = request.getHeaders().containsKey("Signature");
        if (!hasSignature) {
            log.warn("Missing Signature on protected POST " + requestPath.value());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        log.info("Attempting signature verification for: " + requestMethod + " " + requestPath);
        return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(buf -> {
            byte[] bytes = new byte[buf.readableByteCount()];
            buf.read(bytes);
            DataBufferUtils.release(buf);

            Flux<DataBuffer> replay = Flux.defer(() -> Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public @NotNull Flux<DataBuffer> getBody() {return replay;}
            };
            ServerWebExchange mutated = exchange.mutate().request(decorated).build();

            return httpSignatureService.verifySignature(mutated, bytes).flatMap(isValid -> {
                if (isValid) {
                    log.debug("HTTP Signature verified successfully for " + requestMethod + " " + requestPath.value());
                    return chain.filter(mutated);
                } else {
                    log.warn("HTTP Signature verification failed for : " + requestMethod + " " + requestPath.value());
                    mutated.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return mutated.getResponse().setComplete();
                }
            });
        });
    }
}

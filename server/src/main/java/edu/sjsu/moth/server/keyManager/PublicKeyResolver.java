package edu.sjsu.moth.server.keyManager;

import reactor.core.publisher.Mono;

import java.security.PublicKey;

public interface PublicKeyResolver {
    Mono<PublicKey> resolve(String keyId);
}

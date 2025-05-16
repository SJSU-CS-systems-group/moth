package edu.sjsu.moth.server.keyManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.sjsu.moth.util.HttpSignature;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.PublicKey;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

//Why? to make HttpSignatureService.verifySignature() becomes a short chain of pure operators;
@Service
@CommonsLog
public class RemotePublicKeyResolver implements PublicKeyResolver {

    public static final Object NEGATIVE_CACHE_SENTINEL = new Object();
    public final AsyncCache<String, PublicKey> publicKeyCache;
    public final AsyncCache<String, Object> negativeLookupCache;
    private final WebClient webClient;

    // https://www.w3.org/TR/activitypub/#retrieving-objects
    @Autowired
    public RemotePublicKeyResolver(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.defaultHeader(HttpHeaders.ACCEPT,
                                                        "application/activity+json, application/activity+json, " +
                                                                "application/ld+json", MediaType.APPLICATION_JSON_VALUE)
                .build();

        // expires after a year?
        this.publicKeyCache =
                Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofDays(365)).buildAsync();

        this.negativeLookupCache = Caffeine.newBuilder().maximumSize(10_000) // limits the retry count
                .expireAfterWrite(Duration.ofMinutes(10)).buildAsync();
    }

    // https://socialhub.activitypub.rocks/t/verifying-deletes-of-users-who-are-gone/240/1
    // For deleted users: verify only if their key is in cache, otherwise ignore it
    @Override
    public Mono<PublicKey> resolve(String keyId) {
        // check the positive cache first, if found happy!
        CompletableFuture<PublicKey> cachedKey = publicKeyCache.getIfPresent(keyId);
        if (cachedKey != null) {
            return Mono.fromFuture(cachedKey);
        }

        // check negative cache if not in positive cache
        CompletableFuture<Object> negativelyCached = negativeLookupCache.getIfPresent(keyId);
        if (negativelyCached != null) {
            return Mono.fromFuture(negativelyCached).flatMap(v -> Mono.empty()); // Indicates a known miss
        }

        // https://socialhub.activitypub.rocks/t/authorized-fetch-and-the-instance-actor/3868
        // TODO : use activity pub service to fetch the actor, Authorise fetch?

        String actorUrl = keyId.split("#")[0];
        return this.webClient.get().uri(actorUrl).retrieve().bodyToMono(JsonNode.class)
                .subscribeOn(Schedulers.boundedElastic()) // Perform network I/O on boundedElastic
                .flatMap(actorNode -> {
                    JsonNode publicKeyNode = actorNode.path("publicKey").path("publicKeyPem");
                    if (publicKeyNode.isMissingNode() || publicKeyNode.isNull() || !publicKeyNode.isTextual()) {
                        log.warn("publicKeyPem not found or not a string for keyId: " + keyId);
                        return Mono.empty();
                    }
                    String pem = publicKeyNode.asText();
                    try {
                        PublicKey publicKey = HttpSignature.pemToPublicKey(pem);
                        return Mono.justOrEmpty(publicKey);
                    } catch (Exception e) {
                        log.error("Failed to convert PEM to PublicKey for keyId: " + keyId, e);
                        return Mono.empty();
                    }
                }).doOnSuccess(publicKey -> {
                    if (publicKey != null) {
                        publicKeyCache.put(keyId, CompletableFuture.completedFuture(publicKey));
                        log.debug("Cached public key for: " + keyId);
                    } else {
                        negativeLookupCache.put(keyId, CompletableFuture.completedFuture(NEGATIVE_CACHE_SENTINEL));
                        log.debug("Cached negative lookup for: " + keyId);
                    }
                }).doOnError(error -> {
                    negativeLookupCache.put(keyId, CompletableFuture.completedFuture(NEGATIVE_CACHE_SENTINEL));
                    log.warn("Error fetching public key for keyId " + keyId + ", caching negative lookup.", error);
                }).onErrorResume(e -> Mono.empty());

    }
}

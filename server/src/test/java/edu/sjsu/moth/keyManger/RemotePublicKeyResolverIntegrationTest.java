package edu.sjsu.moth.keyManger;

import edu.sjsu.moth.server.keyManager.RemotePublicKeyResolver;
import edu.sjsu.moth.util.HttpSignature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.PublicKey;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = RemotePublicKeyResolverIntegrationTest.TestConfig.class)
class RemotePublicKeyResolverIntegrationTest {

    // actual PEM for @divyamonmastodon@mastodon.social
    private static final String DIVYAMONMASTODON_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApY9HeTdb75SwnhlEP+JI
            ZTIt21qmUijBUawLV2A3EHyidqwackn/m31AN+BirhUxmfpPmKwlTMsnPpCWLFA3
            AGlLJWI3oLfWEJZv8rJpJfNzEbWMMkdJn+3XdN17qxeGjiwOcV00zufIZwpfC08U
            Jgvsx6Gq25mjxwvHU16qMqBf8wi4mhHxcgJKjvjoJCb9oc2KQpLZkt4ysN0DN+IL
            Pw39cZcw38CKJnun0DPotH/lWaXT3CM9FmyEpPPl+LHWMkmtwxu/hNYeYsXfyDeR
            UIStV9wtNi0nb9z+FdxsN9yKSQGzrpoJHOoVN12/irPZdo6adGAS6hQG+pRO901U
            hwIDAQAB
            -----END PUBLIC KEY-----
            """;

    @Autowired
    private RemotePublicKeyResolver publicKeyResolver;
    private PublicKey expectedPublicKey;

    @BeforeEach
    void setUp() {
        expectedPublicKey = HttpSignature.pemToPublicKey(DIVYAMONMASTODON_PUBLIC_KEY_PEM);
        assertNotNull(expectedPublicKey, "The known good PEM should be parseable.");
    }

    // verifies that RemotePublicKeyResolver can fetch and use it
    @Test
    void resolveLiveFetchFromMastodonSocialReturnsCorrectPublicKey() {
        String keyId = "https://mastodon.social/users/divyamonmastodon";

        // remove caches specifically for this keyId to ensure a fetch
        publicKeyResolver.publicKeyCache.synchronous().invalidate(keyId);
        publicKeyResolver.negativeLookupCache.synchronous().invalidate(keyId);

        StepVerifier.create(publicKeyResolver.resolve(keyId)).expectSubscription().assertNext(
                pk -> Assertions.assertArrayEquals(expectedPublicKey.getEncoded(), pk.getEncoded(),
                                                   "Key material differs")).verifyComplete();

        // optionally verify that it is now in the positive cache
        StepVerifier.create(
                        Mono.fromFuture(Objects.requireNonNull(publicKeyResolver.publicKeyCache.getIfPresent(keyId))))
                .expectNext(expectedPublicKey).verifyComplete();
    }

    @Configuration
    static class TestConfig {
        @Bean
        public WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }

        @Bean
        public RemotePublicKeyResolver remotePublicKeyResolver(WebClient.Builder builder) {
            return new RemotePublicKeyResolver(builder);
        }
    }
}

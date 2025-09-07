package edu.sjsu.moth.keyManger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.server.keyManager.RemotePublicKeyResolver;
import edu.sjsu.moth.util.HttpSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.PublicKey;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemotePublicKeyResolverTest {

    private static final String KNOWN_GOOD_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCFENGw33yGihy92pDjZQhl0C3
            6rPJj+CvfSC8+q28hxA161QFNUd13wuCTUcq0Qd2qsBe/2hFyc2DCJJg0h1L78+6
            Z4UMR7EOcpfdUE9Hf3m/hs+FUR45uBJeDK1HSFHD8bHKD6kv8FPGfJTotc+2xjJw
            oYi+1hqp1fIekaxsyQIDAQAB
            -----END PUBLIC KEY-----
            """;
    private static PublicKey expectedTestPublicKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private WebClient.Builder webClientBuilderMock;
    @Mock
    private WebClient webClientMock;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpecMock;
    @Mock
    private WebClient.ResponseSpec responseSpecMock;
    private RemotePublicKeyResolver publicKeyResolver;

    @BeforeEach
    void setUp() {
        expectedTestPublicKey = HttpSignature.pemToPublicKey(KNOWN_GOOD_PUBLIC_KEY_PEM);

        lenient().when(webClientBuilderMock.defaultHeader(eq(HttpHeaders.ACCEPT), anyString(), anyString()))
                .thenReturn(webClientBuilderMock);
        lenient().when(webClientBuilderMock.build()).thenReturn(webClientMock);

        lenient().when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        lenient().when(requestHeadersUriSpecMock.uri(any(String.class))).thenReturn(requestHeadersSpecMock);
        lenient().when(requestHeadersSpecMock.accept(eq(MediaType.valueOf("application/activity+json")),
                                                     eq(MediaType.APPLICATION_JSON)))
                .thenReturn(requestHeadersSpecMock);
        lenient().when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        publicKeyResolver = new RemotePublicKeyResolver(webClientBuilderMock);
    }

    private JsonNode createActorJsonNode(String pem) {
        return objectMapper.createObjectNode().put("id", "http://example.com/users/someUser")
                .set("publicKey", objectMapper.createObjectNode().put("publicKeyPem", pem));
    }

    private JsonNode createActorJsonNodeWithoutPem() {
        return objectMapper.createObjectNode().put("id", "http://example.com/users/someUser")
                .set("publicKey", objectMapper.createObjectNode());
    }

    @Test
    void resolveWhenCacheMissAndFetchSuccessWithKnownGoodPemReturnsKeyAndCaches() throws InterruptedException {
        String keyId = "http://example.com/users/fetchUserGoodKey#main-key";
        JsonNode actorResponse = createActorJsonNode(KNOWN_GOOD_PUBLIC_KEY_PEM);
        when(responseSpecMock.bodyToMono(JsonNode.class)).thenReturn(Mono.just(actorResponse));

        StepVerifier.create(publicKeyResolver.resolve(keyId)).expectNext(expectedTestPublicKey).verifyComplete();

        Thread.sleep(100);

        assertNotNull(publicKeyResolver.publicKeyCache.getIfPresent(keyId));
        assertEquals(expectedTestPublicKey, publicKeyResolver.publicKeyCache.getIfPresent(keyId).join());
        verify(webClientMock).get();
    }

    @Test
    void resolveWhenKeyInPositiveCacheReturnsCachedKeyAndNoNetworkCall() {
        String keyId = "http://example.com/users/cachedUser#main-key";
        publicKeyResolver.publicKeyCache.put(keyId, CompletableFuture.completedFuture(expectedTestPublicKey));

        StepVerifier.create(publicKeyResolver.resolve(keyId)).expectNext(expectedTestPublicKey).verifyComplete();

        verify(webClientMock, never()).get();
    }

    @Test
    void resolveWhenCacheMissAndFetchNotFoundReturnsEmptyAndCachesNegative() throws InterruptedException {
        String keyId = "http://example.com/users/notFoundUser#main-key";
        when(responseSpecMock.bodyToMono(JsonNode.class)).thenReturn(Mono.error(
                WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null)));

        StepVerifier.create(publicKeyResolver.resolve(keyId)).verifyComplete();

        Thread.sleep(100);

        assertNotNull(publicKeyResolver.negativeLookupCache.getIfPresent(keyId));
        verify(webClientMock).get();
    }

    @Test
    void resolveWhenKeyInNegativeCacheReturnsEmptyAndNoNetworkCall() {
        String keyId = "http://example.com/users/negCachedUser#main-key";
        publicKeyResolver.negativeLookupCache.put(keyId, CompletableFuture.completedFuture(
                RemotePublicKeyResolver.NEGATIVE_CACHE_SENTINEL));

        StepVerifier.create(publicKeyResolver.resolve(keyId)).verifyComplete();

        verify(webClientMock, never()).get();
    }

    @Test
    void resolveWhenCacheMissAndActorMissingPemReturnsEmptyAndCachesNegative() throws InterruptedException {
        String keyId = "http://example.com/users/noPemUser#main-key";
        JsonNode actorResponse = createActorJsonNodeWithoutPem();
        when(responseSpecMock.bodyToMono(JsonNode.class)).thenReturn(Mono.just(actorResponse));

        StepVerifier.create(publicKeyResolver.resolve(keyId)).verifyComplete();

        Thread.sleep(100);

        assertNotNull(publicKeyResolver.negativeLookupCache.getIfPresent(keyId));
        verify(webClientMock).get();
    }

    @Test
    void resolveWhenCacheMissAndInvalidPemFormatInResponseReturnsEmptyAndCachesNegative() throws InterruptedException {
        String keyId = "http://example.com/users/invalidPemUser#main-key";
        JsonNode actorResponse = createActorJsonNode("THIS IS NOT A VALID PEM AT ALL TRUST ME ");
        when(responseSpecMock.bodyToMono(JsonNode.class)).thenReturn(Mono.just(actorResponse));

        StepVerifier.create(publicKeyResolver.resolve(keyId)).verifyComplete();

        Thread.sleep(100);

        assertNotNull(publicKeyResolver.negativeLookupCache.getIfPresent(keyId),
                      "Key should be in negative cache due to invalid PEM");
        assertNull(publicKeyResolver.publicKeyCache.getIfPresent(keyId), "Key should not be in positive cache");
        verify(webClientMock).get();
    }
}

package edu.sjsu.moth.service;

import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.service.HttpSignatureService;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpSignatureServiceTest {

    private static final String HARDCODED_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCFENGw33yGihy92pDjZQhl0C3
            6rPJj+CvfSC8+q28hxA161QFNUd13wuCTUcq0Qd2qsBe/2hFyc2DCJJg0h1L78+6
            Z4UMR7EOcpfdUE9Hf3m/hs+FUR45uBJeDK1HSFHD8bHKD6kv8FPGfJTotc+2xjJw
            oYi+1hqp1fIekaxsyQIDAQAB
            -----END PUBLIC KEY-----
            """;
    private static final String HARDCODED_PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMIUQ0bDffIaKHL3
            akONlCGXQLfqs8mP4K99ILz6rbyHEDXrVAU1R3XfC4JNRyrRB3aqwF7/aEXJzYMI
            kmDSHUvvz7pnhQxHsQ5yl91QT0d/eb+Gz4VRHjm4El4MrUdIUcPxscoPqS/wU8Z8
            lOi1z7bGMnChiL7WGqnV8h6RrGzJAgMBAAECgYEAlHxmQJS/HmTO/6612XtPkyei
            t1PVO+hdckZcrtln5S68w1QJ03ZA9ziwGIBBa8vDVxIq3kOwpnxQROlg/Lyk9iec
            MTPZ0NiJp7D37ESm5vJ5bagfhnHvXCoG04qSrCtdr+nN2mK5xFGOTq8TphjsQEGz
            +Du5qdWkaJs5UASyofUCQQDsOSNUfbxYNSB/Weq9+fYqPoJPuchwTeMYmxlnvOVm
            YGYcUM40wtStdH9mbelHmbS0KYGprlEr3m7jXaO3V08jAkEA0lPe/ymeS2HjxtCj
            98p6Xq4RjJuhG0Dn+4e4eRnoVAXs5SQaiByZImW451zm3qEjVWwufRBkSNBkwQ5a
            v7ApIwJBAILiRckSwcC97vug/oe0b8iISfuSnJRdE28WwMTRzOkkkG8v9pEVQnG5
            Er3WOGMLrywDs2wowaDk5dvkjkmPfrECQQCAhPtoU5gEXAaBABCRY0ou/JKApsBl
            FN4sFpykcy5B2XUN92e28DKqkBnSVjREqZYbpoUpqpB85coLJahSJWSdAkBeuWDJ
            IVyL/a54qUgTVCoiItJnxXw6WkUtGdvWnMjtTXJBedMAQVgznrTImXNSk5vVXhxJ
            wZ3frm2JIy/Es69M
            -----END PRIVATE KEY-----
            """;
    private static final PublicKey HARDCODED_PUBLIC_KEY = HttpSignature.pemToPublicKey(HARDCODED_PUBLIC_KEY_PEM);
    private static final PrivateKey HARDCODED_PRIVATE_KEY = HttpSignature.pemToPrivateKey(HARDCODED_PRIVATE_KEY_PEM);
    private static final String TEST_ACCOUNT_ID = "testuser";
    private static final String TEST_SERVER_NAME = "test.server"; // Assuming this value
    private static final String TEST_KEY_ID = "https://" + TEST_SERVER_NAME + "/users/" + TEST_ACCOUNT_ID + "#main-key";
    private static final String REMOTE_ACTOR_URL = "https://remote.example/actor";
    private static final String REMOTE_KEY_ID = REMOTE_ACTOR_URL + "#main-key";
    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-04-08T05:55:00Z"), ZoneOffset.UTC);

    @Mock
    private PubKeyPairRepository pubKeyPairRepository;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private ServerWebExchange exchange;
    @Mock
    private ServerHttpRequest serverRequest;
    private HttpSignatureService httpSignatureService;

    @BeforeAll
    static void initMothConfig() throws IOException {
        // grab MothConfiguration from integration test file
        String fullPath = HttpSignatureServiceTest.class.getResource("/test.cfg").getFile();
        new MothConfiguration(new File(fullPath));
    }

    @BeforeEach
    void setUp() {
        when(webClientBuilder.defaultHeader(anyString(), any(String[].class))).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // mock the service manually
        this.httpSignatureService = new HttpSignatureService(pubKeyPairRepository, webClientBuilder);
    }

    @Test
    void testSignRequestHappyPathWithBodyShouldAddSignature() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        String requestBody = "{\"activity\": \"create\"}";

        Flux<DataBuffer> fluxBody = Flux.just(dataBufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8)));
        ClientRequest originalRequest =
                ClientRequest.create(HttpMethod.POST, URI.create("https://remote.example/inbox"))
                        .body(fluxBody, DataBuffer.class).build();

        DataBuffer dataBuffer = dataBufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<DataBufferUtils> mockedDataBufferUtils = Mockito.mockStatic(DataBufferUtils.class)) {
            mockedDataBufferUtils.when(() -> DataBufferUtils.join(any())).thenReturn(Mono.just(dataBuffer));
            mockedDataBufferUtils.when(() -> DataBufferUtils.release(any())).thenReturn(true);

            Mono<ClientRequest> signedRequestMono = httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID);
            // Assert: Verify a request is returned and it "contains" a Signature header
            StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
                assertTrue(signedRequest.headers().containsKey("Signature"),
                           "Should contain Signature header"); // TODO : check thoroughly
                String sigHeader = signedRequest.headers().getFirst("Signature");
                return sigHeader != null && sigHeader.startsWith("keyId=") && sigHeader.contains("headers=") &&
                        sigHeader.contains("signature=");
            }).verifyComplete();
        }
    }

    @Test
    void testSignRequestSingleDateHeader() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        String requestBody = "{\"activity\": \"create\"}";

        Flux<DataBuffer> fluxBody = Flux.just(dataBufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8)));
        ClientRequest originalRequest =
                ClientRequest.create(HttpMethod.POST, URI.create("https://remote.example/inbox"))
                        .body(fluxBody, DataBuffer.class).build();

        DataBuffer dataBuffer = dataBufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<DataBufferUtils> mockedDataBufferUtils = Mockito.mockStatic(DataBufferUtils.class)) {
            mockedDataBufferUtils.when(() -> DataBufferUtils.join(any())).thenReturn(Mono.just(dataBuffer));
            mockedDataBufferUtils.when(() -> DataBufferUtils.release(any())).thenReturn(true);

            Mono<ClientRequest> signedRequestMono = httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID);
            // Assert: Verify a request is returned and it "contains" a Signature header
            StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
                long dateHeaderCount = signedRequest.headers().entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(HttpHeaders.DATE))
                        .flatMap(entry -> entry.getValue().stream())
                        .count();
                System.out.println("Date headers: " + signedRequest.headers().get(HttpHeaders.DATE));

                assertEquals(1, dateHeaderCount, "There should only be one Date header");
                return dateHeaderCount==1;
            }).verifyComplete();
        }
    }

    @Test
    void testSignRequestHappyPathWithoutBodyShouldAddSignature() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        ClientRequest originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://remote.example/actor"))
                .build(); // No body at all

        Mono<ClientRequest> signedRequestMono = httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID);

        StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
            assertTrue(signedRequest.headers().containsKey("Signature"), "Should contain Signature header");
            assertFalse(signedRequest.headers().containsKey("Digest"), "Should NOT contain Digest header");
            String sigHeader = signedRequest.headers().getFirst("Signature");
            return sigHeader != null && sigHeader.startsWith("keyId=") && sigHeader.contains("headers=") &&
                    sigHeader.contains("signature=");
        }).verifyComplete();
    }

    @Test
    void testVerifySignatureHappyPathValidSignatureShouldReturnTrue() throws Exception {
        String method = "GET";
        URI uri = URI.create("/actor");

        String dateHeaderValue =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)); // current time

        HttpHeaders headersForSigning = new HttpHeaders();
        headersForSigning.add(HttpHeaders.HOST, TEST_SERVER_NAME);
        headersForSigning.add(HttpHeaders.DATE, dateHeaderValue);

        String signatureHeaderValue = HttpSignature.generateSignatureHeader(method, uri, headersForSigning,
                                                                            List.of("(request-target)", "host", "date"),
                                                                            HARDCODED_PRIVATE_KEY, REMOTE_KEY_ID);

        // HttpHeaders for the mock request
        HttpHeaders actualRequestHeaders = new HttpHeaders();
        actualRequestHeaders.add("Signature", signatureHeaderValue);
        actualRequestHeaders.add(HttpHeaders.DATE, dateHeaderValue);
        actualRequestHeaders.add(HttpHeaders.HOST, TEST_SERVER_NAME);

        when(exchange.getRequest()).thenReturn(serverRequest);
        when(serverRequest.getMethod()).thenReturn(HttpMethod.GET);
        when(serverRequest.getURI()).thenReturn(uri);
        when(serverRequest.getHeaders()).thenReturn(actualRequestHeaders);

        // Mocking WebClient
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq(REMOTE_ACTOR_URL))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // Mocking actor response
        Map<String, Object> publicKeyMap = Map.of("publicKeyPem", HARDCODED_PUBLIC_KEY_PEM);
        Map<String, Object> actorMap = Map.of("publicKey", publicKeyMap);
        when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(Mono.just(actorMap));

        Mono<Boolean> result = httpSignatureService.verifySignature(exchange);

        StepVerifier.create(result).expectNext(true).verifyComplete();
    }
}

package edu.sjsu.moth.service;

import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.service.HttpSignatureService;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.sjsu.moth.server.util.MothConfiguration.mothConfiguration;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertNotNull;

@ExtendWith(MockitoExtension.class)
class HttpSignatureServiceTest {

    public static final URI EXAMPLE_URI = URI.create("/foo?param=value&pet=dog");
    public static final HttpHeaders EXAMPLE_HEADERS = new HttpHeaders(new MultiValueMapAdapter<>(
            Map.of("host", List.of("example.com"), "date", List.of("Sun, 05 Jan 2014 21:31:40 GMT"), "content-Type",
                   List.of("application/json"), "digest",
                   List.of("SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE="), "content-Length", List.of("18"))));
    public static final byte[] EXAMPLE_BODY = "{\"hello\": \"world\"}".getBytes();
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
    private static final String TEST_ACCOUNT_ID = "testuser";
    private static final String TEST_SERVER_NAME = "test.server"; // Assuming this value
    private static final String EXPECTED_KEY_ID =
            "https://" + TEST_SERVER_NAME + "/users/" + TEST_ACCOUNT_ID + "#main-key";
    private static final Pattern HEADERS_PATTERN = Pattern.compile("headers=\"([^\"]+)\"");
    private static final URI TEST_TARGET_URI = URI.create("https://verify.example.com/inbox");

    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    @Mock
    private PubKeyPairRepository pubKeyPairRepository;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    private HttpSignatureService httpSignatureService;

    @BeforeAll
    static void initMothConfig() throws IOException {
        String fullPath = HttpSignatureServiceTest.class.getResource("/test.cfg").getFile();
        new MothConfiguration(new File(fullPath));
    }

    private static @NotNull String getString(String correctKeyId) {
        String correctSignedHeaders = "(request-target) host date";
        String correctSignatureValue = "qdx+H7PHHDZgy4y/Ahn9Tny9V3GP6YgBPyUXMmoxWtLbHpUnXS2mg2" +
                "+SbrQDMCJypxBLSPQR2aAjn7ndmw2iicw3HMbe8VfEdKFYRqzic+efkb3nndiv" +
                "/x1xSHDJWeSWkx3ButlYSuBskLu6kd9Fswtemr3lgdDEmn04swr2Os0=";
        String invalidSignatureValue = correctSignatureValue.replace("qdx", "xxx");

        String signatureHeaderWithInvalidSig =
                String.format("keyId=\"%s\",headers=\"%s\",signature=\"%s\"", correctKeyId, correctSignedHeaders,
                              invalidSignatureValue);
        return signatureHeaderWithInvalidSig;
    }

    @BeforeEach
    void setUp() {
        when(webClientBuilder.defaultHeader(anyString(), any(String[].class))).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        httpSignatureService = spy(new HttpSignatureService(pubKeyPairRepository, webClientBuilder));
    }

    @Test
    void signRequest_HappyPath_WithBody_ShouldAddSignatureAndDigest() throws NoSuchAlgorithmException { // Added
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));
        String requestBody = "{\"activity\": \"create\"}";
        byte[] requestBodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(requestBodyBytes);
        String expectedDigestValue = "SHA-256=" + Base64.getEncoder().encodeToString(hash);
        Flux<DataBuffer> fluxBody = Flux.just(dataBufferFactory.wrap(requestBodyBytes));

        ClientRequest originalRequest =
                ClientRequest.create(HttpMethod.POST, URI.create("https://remote.example/inbox"))
                        .header(HttpHeaders.HOST, "remote.example") // Host is usually needed for signature
                        .body(fluxBody, DataBuffer.class).build();
        Mono<ClientRequest> signedRequestMono =
                httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID, requestBodyBytes);

        StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
            HttpHeaders headers = signedRequest.headers();
            assertTrue(headers.containsKey("Digest"), "Should contain Digest header");
            assertEquals("Digest header value mismatch", expectedDigestValue.substring(3),
                         Objects.requireNonNull(headers.getFirst("Digest")).substring(3));
            assertTrue(headers.containsKey(HttpHeaders.DATE), "Should contain Date header");
            assertTrue(headers.containsKey("Signature"), "Should contain Signature header");
            String sigHeader = headers.getFirst("Signature");
            assertNotNull(sigHeader, "Signature header should not be null");
            assertTrue(sigHeader.contains("keyId"), "Signature keyId mismatch");
            assertTrue(sigHeader.contains("signature=\""), "Signature should contain signature part");
            Matcher matcher = HEADERS_PATTERN.matcher(sigHeader);
            assertTrue(matcher.find(), "Could not find headers part in Signature");
            String signedHeadersString = matcher.group(1);
            assertNotNull(signedHeadersString, "Signed headers string is null");
            List<String> signedHeadersList = List.of(signedHeadersString.toLowerCase().split(" "));
            assertTrue(signedHeadersList.contains("(request-target)"),
                       "Signature headers should include (request-target)");
            assertTrue(signedHeadersList.contains("host"), "Signature headers should include host");
            assertTrue(signedHeadersList.contains("date"), "Signature headers should include date");

            return true;
        }).verifyComplete();
    }

    @Test
    void signRequest_HappyPath_WithoutBody_ShouldAddSignature() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        ClientRequest originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://remote.example/actor"))
                .header(HttpHeaders.HOST, "remote.example").build();
        Mono<ClientRequest> signedRequestMono =
                httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID, null);

        StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
            HttpHeaders headers = signedRequest.headers();
            assertFalse(headers.containsKey("Digest"), "Should NOT contain Digest header for GET request");
            assertTrue(headers.containsKey("Signature"), "Should contain Signature header");
            String sigHeader = headers.getFirst("Signature");
            assertNotNull(sigHeader, "Signature header should not be null");
            assertTrue(sigHeader.contains("keyId"), "Signature keyId mismatch");
            assertTrue(sigHeader.contains("signature=\""), "Signature should contain signature part");
            Matcher matcher = HEADERS_PATTERN.matcher(sigHeader);
            assertTrue(matcher.find(), "Could not find headers part in Signature");
            String signedHeadersString = matcher.group(1);
            assertNotNull(signedHeadersString, "Signed headers string is null");
            List<String> signedHeadersList = List.of(signedHeadersString.toLowerCase().split(" "));
            assertTrue(signedHeadersList.contains("(request-target)"),
                       "Signature headers should include (request-target)");
            assertTrue(signedHeadersList.contains("host"), "Signature headers should include host");
            assertTrue(signedHeadersList.contains("date"), "Signature headers should include date");
            assertFalse(signedHeadersList.contains("digest"), "Signature headers should NOT include digest");
            return true;
        }).verifyComplete();
    }

    /*
     * example request from spec:
     *  POST /foo?param=value&pet=dog HTTP/1.1
     * Host: example.com
     * Date: Sun, 05 Jan 2014 21:31:40 GMT
     * Content-Type: application/json
     * Digest: SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
     * Content-Length: 18
     *
     *{"hello": "world"}
     */
    // https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures-12#appendix-C.2
    @Test
    void signRequest_ShouldGenerateCorrectSignature() {
        URI uri = EXAMPLE_URI;
        HttpHeaders initialHeaders = new HttpHeaders();
        initialHeaders.setAll(EXAMPLE_HEADERS.toSingleValueMap());
        byte[] bodyBytes = EXAMPLE_BODY;
        Flux<DataBuffer> fluxBody = Flux.just(dataBufferFactory.wrap(bodyBytes));
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        ClientRequest originalRequest = ClientRequest.create(HttpMethod.POST, URI.create(
                        "https://example.com" + uri.getPath() + "?" + uri.getQuery())).headers(h -> h.addAll(initialHeaders))
                .body(fluxBody, DataBuffer.class).build();
        Mono<ClientRequest> signedRequestMono =
                httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID, bodyBytes)
                        .doOnNext(signedRequest -> {
                            HttpHeaders finalHeaders = signedRequest.headers();
                            String signatureHeader = finalHeaders.getFirst("Signature");
                            var actual = finalHeaders.getFirst("Signature");
                            var expected = """
                                    keyId="Test",headers="(request-target) host date",signature="qdx+H7PHHDZgy4y/Ahn9Tny9V3GP6YgBPyUXMmoxWtLbHpUnXS2mg2+SbrQDMCJypxBLSPQR2aAjn7ndmw2iicw3HMbe8VfEdKFYRqzic+efkb3nndiv/x1xSHDJWeSWkx3ButlYSuBskLu6kd9Fswtemr3lgdDEmn04swr2Os0=\"""";
                            Assertions.assertEquals(HttpSignature.extractSignature(expected),
                                                    HttpSignature.extractSignature(actual));
                        });
    }

    // Verifies Date, Host Header is added with correct values
    @Test
    void signRequest_AddsDateAndHostHeaders_WhenMissing() {
        String accountId = TEST_ACCOUNT_ID;
        URI requestUri = URI.create("https://some.target.host/api/v1/resource");
        String expectedHost = requestUri.getHost(); // "some.target.host"
        String expectedDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        PubKeyPair keyPair = new PubKeyPair(accountId, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(accountId)).thenReturn(Mono.just(keyPair));

        ClientRequest originalRequest = ClientRequest.create(HttpMethod.GET, requestUri).build();
        Mono<ClientRequest> signedRequestMono = httpSignatureService.signRequest(originalRequest, accountId, null);

        StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
            HttpHeaders headers = signedRequest.headers();
            assertTrue(headers.containsKey(HttpHeaders.DATE), "Date header should be added");
            assertEquals("Added Date header value mismatch", expectedDate, headers.getFirst(HttpHeaders.DATE));
            assertTrue(headers.containsKey(HttpHeaders.HOST), "Host header should be added");
            assertEquals("Added Host header value mismatch", expectedHost, headers.getFirst(HttpHeaders.HOST));
            assertTrue(headers.containsKey("Signature"), "Signature header should be present");

            return true;
        }).verifyComplete();
    }

    @Test
    void signRequest_ShouldNotAddDateAndHostHeaders() {
        String accountId = TEST_ACCOUNT_ID;
        URI requestUri = URI.create("https://provided.host/specific/path");
        String predefinedHost = "provided.host";
        String predefinedDate = "Tue, 29 Feb 2000 12:00:00 GMT";

        PubKeyPair keyPair = new PubKeyPair(accountId, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(accountId)).thenReturn(Mono.just(keyPair));

        ClientRequest originalRequest =
                ClientRequest.create(HttpMethod.GET, requestUri).header(HttpHeaders.HOST, predefinedHost)
                        .header(HttpHeaders.DATE, predefinedDate).build();
        Mono<ClientRequest> signedRequestMono =
                httpSignatureService.signRequest(originalRequest, accountId, null); // Body null for simplicity

        StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
            HttpHeaders headers = signedRequest.headers();
            assertTrue(headers.containsKey(HttpHeaders.HOST), "Host header should still be present");
            assertEquals("Existing Host header value mismatch", predefinedHost, headers.getFirst(HttpHeaders.HOST));
            assertTrue(headers.containsKey(HttpHeaders.DATE), "Date header should still be present");
            assertEquals("Existing Date header value mismatch", predefinedDate, headers.getFirst(HttpHeaders.DATE));

            return true;
        }).verifyComplete();
    }

    @Test
    void signRequest_ShouldContainCorrectKeyId() {
        String accountId = TEST_ACCOUNT_ID;
        URI requestUri = URI.create("https://some.target.host/api/v1/resource");
        String expectedServerName = mothConfiguration.getServerName();
        String expectedKeyId = "https://" + expectedServerName + "/users/" + accountId + "#main-key";
        PubKeyPair keyPair = new PubKeyPair(accountId, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(accountId)).thenReturn(Mono.just(keyPair));

        ClientRequest originalRequest =
                ClientRequest.create(HttpMethod.GET, requestUri).header(HttpHeaders.HOST, requestUri.getHost()).build();
        Mono<ClientRequest> signedRequestMono = httpSignatureService.signRequest(originalRequest, accountId, null);
        StepVerifier.create(signedRequestMono).expectNextMatches(signedRequest -> {
            HttpHeaders headers = signedRequest.headers();
            String signatureHeaderValue = headers.getFirst("Signature");
            String expectedKeyIdParam = "keyId=\"" + expectedKeyId + "\"";
            assertTrue(signatureHeaderValue.contains(expectedKeyIdParam),
                       "Signature header should contain correct keyId parameter");
            return true;
        }).verifyComplete();
    }

    @Test
    void verifySignature_MissingSignatureHeader_ShouldReturnFalse() {
        // https://stackoverflow.com/questions/47878963/mockito-fails-with-inlined-mocks-enabled-with-invalid-paramter-name-exception
        MockServerHttpRequest mockServerRequest = MockServerHttpRequest.get(TEST_TARGET_URI.toString()).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);

        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();

        verify(httpSignatureService, never()).fetchPublicKey(anyString());
    }

    @Test
    void verifySignature_InvalidSignatureValue_ShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader = "keyId=\"" + EXPECTED_KEY_ID +
                "\",headers=\"(request-target) host date\",signature=\"invalidsignaturevalue\"";
        headers.add("Signature", signatureHeader);

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        doReturn(Mono.just(HARDCODED_PUBLIC_KEY)).when(httpSignatureService).fetchPublicKey(eq(EXPECTED_KEY_ID));
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void verifySignature_InvalidSignatureValue_UsingC2Data_ShouldReturnFalse() {
        URI uri = EXAMPLE_URI;
        HttpHeaders c2Headers = new HttpHeaders();
        c2Headers.addAll(EXAMPLE_HEADERS);
        String correctKeyId = "Test";
        String signatureHeaderWithInvalidSig = getString(correctKeyId);
        c2Headers.set("Signature", signatureHeaderWithInvalidSig);

        URI fullRequestUri = URI.create("https://example.com" + uri);
        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.post(fullRequestUri.toString()).headers(c2Headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        doReturn(Mono.just(HARDCODED_PUBLIC_KEY)).when(httpSignatureService).fetchPublicKey(eq(correctKeyId));

        // https://stackoverflow.com/questions/20551926/exception-mockito-wanted-but-not-invoked-actually-there-were-zero-interaction
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
    }

    @Test
    void verifySignature_ExpiredDateHeader_ShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        String expiredDate = HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(25));
        headers.add(HttpHeaders.DATE, expiredDate);
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader =
                "keyId=\"" + EXPECTED_KEY_ID + "\",headers=\"(request-target) host date\",signature=\"placeholder\"";
        headers.add("Signature", signatureHeader);

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();

        verify(httpSignatureService, never()).fetchPublicKey(anyString());
    }

    @Test
    void verifySignature_FetchPublicKeyFails_ShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader = "keyId=\"" + EXPECTED_KEY_ID +
                "\",headers=\"(request-target) host date\",signature=\"validlooking\""; // signature value doesn't
        headers.add("Signature", signatureHeader);

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        doReturn(Mono.empty()).when(httpSignatureService).fetchPublicKey(eq(EXPECTED_KEY_ID));
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
        verify(httpSignatureService, times(1)).fetchPublicKey(eq(EXPECTED_KEY_ID));
    }

    @Test
    void signThenVerify_HappyPath_WithBody() {
        HttpMethod method = HttpMethod.POST;
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(
                Mono.just(new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM)));
        ClientRequest originalRequest =
                ClientRequest.create(method, TEST_TARGET_URI).header(HttpHeaders.HOST, TEST_TARGET_URI.getHost())
                        .body(Flux.just(dataBufferFactory.wrap(EXAMPLE_BODY)), DataBuffer.class).build();

        System.out.println(originalRequest);
        Mono<ClientRequest> signedRequestMono =
                httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID, EXAMPLE_BODY);
        ClientRequest signedRequest = signedRequestMono.block();
        assertNotNull(String.valueOf(signedRequest), "Signed request mono resolved to null");
        HttpHeaders signedHeaders = signedRequest.headers();
        assertTrue(signedHeaders.containsKey("Signature"), "Signature header should be present after signing");
        assertTrue(signedHeaders.containsKey("Digest"), "Digest header should be present for POST with body");
        String signatureHeaderValue = signedHeaders.getFirst("Signature");
        String actualKeyId = HttpSignature.extractKeyId(signatureHeaderValue);
        assertTrue(!actualKeyId.isEmpty());

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.method(signedRequest.method(), signedRequest.url()).headers(signedHeaders)
                        .build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        doReturn(Mono.just(HARDCODED_PUBLIC_KEY)).when(httpSignatureService).fetchPublicKey(eq(actualKeyId));

        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(true) // Expect successful verification
                .verifyComplete();
    }

}

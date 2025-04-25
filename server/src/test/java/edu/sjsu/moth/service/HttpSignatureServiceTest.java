package edu.sjsu.moth.service;

import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.service.HttpSignatureService;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import org.jetbrains.annotations.NotNull;
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

    public static final URI example_uri = URI.create("/foo?param=value&pet=dog");
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
    public static HttpHeaders example_headers = new HttpHeaders(new MultiValueMapAdapter<>(
            Map.of("host", List.of("example.com"), "date", List.of("Sun, 05 Jan 2014 21:31:40 GMT"), "content-Type",
                   List.of("application/json"), "content-Length", List.of("18"))));
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
        String fullPath = Objects.requireNonNull(HttpSignatureServiceTest.class.getResource("/test.cfg")).getFile();
        new MothConfiguration(new File(fullPath));
    }

    private static @NotNull String generateC2InvalidSignature(String correctKeyId) {
        String correctSignedHeaders = "(request-target) host date"; // Type C2 does not have digest
        String correctSignatureValue = "qdx+H7PHHDZgy4y/Ahn9Tny9V3GP6YgBPyUXMmoxWtLbHpUnXS2mg2" +
                "+SbrQDMCJypxBLSPQR2aAjn7ndmw2iicw3HMbe8VfEdKFYRqzic+efkb3nndiv" +
                "/x1xSHDJWeSWkx3ButlYSuBskLu6kd9Fswtemr3lgdDEmn04swr2Os0=";
        String invalidSignatureValue = correctSignatureValue.replace("qdx", "xxx");

        return String.format("keyId=\"%s\",headers=\"%s\",signature=\"%s\"", correctKeyId, correctSignedHeaders,
                             invalidSignatureValue);
    }

    @BeforeEach
    void setUp() {
        when(webClientBuilder.defaultHeader(anyString(), any(String[].class))).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        httpSignatureService = spy(new HttpSignatureService(pubKeyPairRepository, webClientBuilder));
    }

    @Test
    void signRequestHappyPathWithBodyShouldAddSignatureAndDigest() throws NoSuchAlgorithmException { // Added
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
            assert sigHeader != null;
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
    void testSignRequestHappyPathWithoutBodyShouldAddSignature() {
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
            assert sigHeader != null;
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
    void signRequestC2ShouldGenerateCorrectSignature() {
        // C2 body
        HttpMethod method = HttpMethod.POST;
        URI c2RequestUri = URI.create("https://example.com/foo?param=value&pet=dog");
        String c2TestAccountID = "C2TestAccount";
        HttpHeaders initialHeaders = new HttpHeaders();
        initialHeaders.setAll(example_headers.toSingleValueMap());
        initialHeaders.set(HttpHeaders.HOST, "example.com");
        initialHeaders.set(HttpHeaders.DATE, "Sun, 05 Jan 2014 21:31:40 GMT");

        PubKeyPair c2KeyPair = new PubKeyPair(c2TestAccountID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(c2TestAccountID)).thenReturn(Mono.just(c2KeyPair));

        ClientRequest originalRequest =
                ClientRequest.create(method, c2RequestUri).headers(h -> h.addAll(initialHeaders))
                        // C2 signature doesn't include digest
                        .build();

        String expectedC2SignatureValue = "qdx+H7PHHDZgy4y/Ahn9Tny9V3GP6YgBPyUXMmoxWtLbHpUnXS2mg2" +
                "+SbrQDMCJypxBLSPQR2aAjn7ndmw2iicw3HMbe8VfEdKFYRqzic+efkb3nndiv" +
                "/x1xSHDJWeSWkx3ButlYSuBskLu6kd9Fswtemr3lgdDEmn04swr2Os0=";
        String expectedC2HeadersField = "(request-target) host date";

        ClientRequest signedRequest = httpSignatureService.signRequest(originalRequest, c2TestAccountID, null).block();

        assertNotNull(String.valueOf(signedRequest), "Signed request should not be null");
        assert signedRequest != null;
        HttpHeaders signedHeaders = signedRequest.headers();
        assertTrue(signedHeaders.containsKey("Signature"), "Signature header missing after signing");
        assertFalse(signedHeaders.containsKey("Digest"), "Digest header should not be present for null body");

        String actualSignatureHeader = signedHeaders.getFirst("Signature");
        assertNotNull(actualSignatureHeader, "Signature header value is null");

        // Verify signature value with expected value
        String actualSignatureValue = HttpSignature.extractFields(actualSignatureHeader).get("signature");
        assertEquals("Generated signature value does not match C.2 example", expectedC2SignatureValue,
                     actualSignatureValue);

        // C2 Signature should only have "(request-target), host, and date"
        String actualHeadersField = HttpSignature.extractFields(actualSignatureHeader).get("headers");
        assertEquals("Signed headers field is incorrect", expectedC2HeadersField, actualHeadersField.toLowerCase());
    }

    // Verifies Date, Host Header is added with correct values
    @Test
    void signRequestAddsDateAndHostHeaders_WhenMissing() {
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
    void signRequestShouldNotAddDateAndHostHeaders() {
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
    void signRequestShouldContainCorrectKeyId() {
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
            assert signatureHeaderValue != null;
            assertTrue(signatureHeaderValue.contains(expectedKeyIdParam),
                       "Signature header should contain correct keyId parameter");
            return true;
        }).verifyComplete();
    }

    @Test
    void verifySignatureMissingSignatureHeaderShouldReturnFalse() {
        // https://stackoverflow.com/questions/47878963/mockito-fails-with-inlined-mocks-enabled-with-invalid-paramter-name-exception
        MockServerHttpRequest mockServerRequest = MockServerHttpRequest.get(TEST_TARGET_URI.toString()).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);

        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();

        verify(httpSignatureService, never()).fetchPublicKey(anyString());
    }

    @Test
    void verifySignatureInvalidSignatureValueShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader = "keyId=\"" + EXPECTED_KEY_ID +
                "\",headers=\"(request-target) host date\",signature=\"invalidsignaturevalue\"";
        headers.add("Signature", signatureHeader);

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        assert HARDCODED_PUBLIC_KEY != null;
        doReturn(Mono.just(HARDCODED_PUBLIC_KEY)).when(httpSignatureService).fetchPublicKey(eq(EXPECTED_KEY_ID));
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void verifySignatureInvalidSignatureValueUsingC2DataShouldReturnFalse() {
        HttpHeaders c2Headers = new HttpHeaders();
        c2Headers.addAll(example_headers);
        String correctKeyId = "Test";
        String signatureHeaderWithInvalidSig = generateC2InvalidSignature(correctKeyId);
        c2Headers.set("Signature", signatureHeaderWithInvalidSig);

        URI fullRequestUri = URI.create("https://example.com" + example_uri);
        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.post(fullRequestUri.toString()).headers(c2Headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        assert HARDCODED_PUBLIC_KEY != null;
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
    void verifySignatureFetchPublicKeyFailsShouldReturnFalse() {
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
    void signThenVerifyHappyPathWithBody() {
        HttpMethod method = HttpMethod.POST;
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(
                Mono.just(new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM)));
        ClientRequest originalRequest =
                ClientRequest.create(method, TEST_TARGET_URI).header(HttpHeaders.HOST, TEST_TARGET_URI.getHost())
                        .body(Flux.just(dataBufferFactory.wrap(EXAMPLE_BODY)), DataBuffer.class).build();

        Mono<ClientRequest> signedRequestMono =
                httpSignatureService.signRequest(originalRequest, TEST_ACCOUNT_ID, EXAMPLE_BODY);
        ClientRequest signedRequest = signedRequestMono.block();
        assertNotNull(String.valueOf(signedRequest), "Signed request mono resolved to null");
        assert signedRequest != null;
        HttpHeaders signedHeaders = signedRequest.headers();
        assertTrue(signedHeaders.containsKey("Signature"), "Signature header should be present after signing");
        assertTrue(signedHeaders.containsKey("Digest"), "Digest header should be present for POST with body");
        String signatureHeaderValue = signedHeaders.getFirst("Signature");
        String actualKeyId = HttpSignature.extractKeyId(signatureHeaderValue);
        assert actualKeyId != null;
        assertFalse(actualKeyId.isEmpty());

        Flux<DataBuffer> bodyFluxForVerification = Flux.just(dataBufferFactory.wrap(EXAMPLE_BODY));
        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.method(signedRequest.method(), signedRequest.url()).headers(signedHeaders)
                        .body(bodyFluxForVerification);
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        assert HARDCODED_PUBLIC_KEY != null;
        doReturn(Mono.just(HARDCODED_PUBLIC_KEY)).when(httpSignatureService).fetchPublicKey(eq(actualKeyId));

        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange);
        StepVerifier.create(verificationResultMono).expectNext(true) // Expect successful verification
                .verifyComplete();
    }

}

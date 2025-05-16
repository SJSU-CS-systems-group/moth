package edu.sjsu.moth.service;

import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.keyManager.PublicKeyResolver;
import edu.sjsu.moth.server.service.HttpSignatureService;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@ExtendWith(MockitoExtension.class)
class HttpSignatureServiceTest {

    public static final byte[] EXAMPLE_BODY = "{\"hello\": \"world\"}".getBytes(StandardCharsets.UTF_8);
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
    private static final String TEST_SERVER_NAME = "localhost"; // Assuming this value
    private static final String EXPECTED_KEY_ID =
            "https://" + TEST_SERVER_NAME + "/users/" + TEST_ACCOUNT_ID + "#main-key";
    private static final URI TEST_TARGET_URI = URI.create("https://verify.example.com/inbox");
    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    @Mock
    private PubKeyPairRepository pubKeyPairRepository;
    @Mock
    private PublicKeyResolver publicKeyResolver;

    private HttpSignatureService httpSignatureService;

    @BeforeAll
    static void initMothConfig() throws IOException {
        String fullPath = Objects.requireNonNull(HttpSignatureServiceTest.class.getResource("/test.cfg")).getFile();
        new MothConfiguration(new File(fullPath));
    }

    @BeforeEach
    void setUp() {
        httpSignatureService = new HttpSignatureService(pubKeyPairRepository, publicKeyResolver);
    }

    // test cases for prepareSignedHeaders method
    @Test
    void prepareSignedHeadersPOSTWithBodyShouldCreateCorrectHeaders() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        URI targetUri = URI.create("https://target.example.com/inbox");

        Mono<HttpHeaders> headersMono =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, targetUri, EXAMPLE_BODY);

        StepVerifier.create(headersMono).expectNextMatches(headers -> {
            assertTrue(headers.containsKey(HttpHeaders.DATE), "Should contain Date header");
            assertEquals("Host header mismatch", targetUri.getHost(), headers.getFirst(HttpHeaders.HOST));
            assertTrue(headers.containsKey("Digest"), "Should contain Digest header");
            assertTrue(headers.containsKey("Signature"), "Should contain Signature header");

            String sigHeader = headers.getFirst("Signature");
            assertNotNull(sigHeader, "Signature header is null");
            assertTrue(sigHeader.contains("keyId=\"" + EXPECTED_KEY_ID + "\""),
                       "keyId mismatch in Signature. Expected: " + EXPECTED_KEY_ID + " Got: " + sigHeader);

            Map<String, String> sigFields = HttpSignature.extractFields(sigHeader);
            String signedHeadersString = sigFields.get("headers");
            assertNotNull(signedHeadersString, "headers field missing in Signature");
            List<String> signedHeadersList = List.of(signedHeadersString.toLowerCase().split(" "));
            assertTrue(signedHeadersList.containsAll(List.of("(request-target)", "host", "date", "digest")),
                       "Signed headers list mismatch for POST");
            return true;
        }).verifyComplete();
    }

    @Test
    void prepareSignedHeadersGETWithoutBodyShouldCreateCorrectHeaders() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        URI targetUri = URI.create("https://target.example.com/actor");

        Mono<HttpHeaders> headersMono =
                httpSignatureService.prepareSignedHeaders(HttpMethod.GET, TEST_ACCOUNT_ID, targetUri, null);

        StepVerifier.create(headersMono).expectNextMatches(headers -> {
            assertTrue(headers.containsKey(HttpHeaders.DATE), "Should contain Date header");
            assertEquals("Host header mismatch", targetUri.getHost(), headers.getFirst(HttpHeaders.HOST));
            assertTrue(headers.containsKey(HttpHeaders.ACCEPT), "Should contain Accept header");
            assertFalse(headers.containsKey("Digest"), "Should NOT contain Digest header for GET");
            assertFalse(headers.containsKey(HttpHeaders.CONTENT_TYPE), "Should NOT contain Content-Type for GET");
            assertTrue(headers.containsKey("Signature"), "Should contain Signature header");

            String sigHeader = headers.getFirst("Signature");
            assert sigHeader != null;
            assertTrue(sigHeader.contains("keyId=\"" + EXPECTED_KEY_ID + "\""), "keyId mismatch");
            Map<String, String> sigFields = HttpSignature.extractFields(sigHeader);
            String signedHeadersString = sigFields.get("headers");
            List<String> signedHeadersList = List.of(signedHeadersString.toLowerCase().split(" "));
            assertTrue(signedHeadersList.containsAll(List.of("(request-target)", "host", "date")),
                       "Signed headers list mismatch for GET");
            assertFalse(signedHeadersList.contains("digest"), "Digest should not be in signed headers for GET");
            return true;
        }).verifyComplete();
    }

    @Test
    void prepareSignedHeadersKeyNotFoundShouldReturnError() {
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.empty());
        URI targetUri = URI.create("https://target.example.com/inbox");
        Mono<HttpHeaders> headersMono =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, targetUri, EXAMPLE_BODY);

        StepVerifier.create(headersMono).expectErrorMessage("Private key not found for actor: " + TEST_ACCOUNT_ID)
                .verify();
    }

    @Test
    void prepareSignedHeadersSignatureGenerationExceptionShouldReturnError() {
        // PEM to PrivateKey conversion fails
        PubKeyPair keyPairWithBadKey =
                new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, "ofc invalid private key data!");
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPairWithBadKey));
        URI targetUri = URI.create("https://target.example.com/inbox");

        Mono<HttpHeaders> headersMono =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, targetUri, EXAMPLE_BODY);

        StepVerifier.create(headersMono).expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                "Failed to prepare signed headers".equals(throwable.getMessage())).verify();
    }

    // test cases for verifySignature method

    @Test
    void verifySignatureMissingSignatureHeaderShouldReturnFalse() {
        MockServerHttpRequest mockServerRequest = MockServerHttpRequest.get(TEST_TARGET_URI.toString()).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, new byte[0]);

        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
        verify(publicKeyResolver, never()).resolve(anyString());
    }

    @Test
    void verifySignatureInvalidSignatureValueShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        // for GET digest is not required to be signed
        String signatureHeader = "keyId=\"" + EXPECTED_KEY_ID +
                "\",headers=\"(request-target) host date\",signature=\"invalidsignaturevalue\"";
        headers.add("Signature", signatureHeader);

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        assert HARDCODED_PUBLIC_KEY != null;
        when(publicKeyResolver.resolve(eq(EXPECTED_KEY_ID))).thenReturn(Mono.just(HARDCODED_PUBLIC_KEY));

        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, new byte[0]);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
    }

    @Test
    void verifySignatureExpiredDateHeaderShouldReturnFalse() {
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
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, new byte[0]);

        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
        verify(publicKeyResolver, never()).resolve(anyString());
    }

    @Test
    void verifySignaturePublicKeyResolverFailsShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader =
                "keyId=\"" + EXPECTED_KEY_ID + "\",headers=\"(request-target) host date\",signature=\"validlooking\"";
        headers.add("Signature", signatureHeader);

        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        when(publicKeyResolver.resolve(eq(EXPECTED_KEY_ID))).thenReturn(Mono.empty());
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, new byte[0]);
        StepVerifier.create(verificationResultMono).expectNext(false).verifyComplete();
        verify(publicKeyResolver, times(1)).resolve(eq(EXPECTED_KEY_ID));
    }

    // missing 'date' or '(created)' in headers
    @Test
    void verifySignatureWithMissingDateOrCreatedInSignedHeadersShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader =
                "keyId=\"" + EXPECTED_KEY_ID + "\",headers=\"(request-target) host\",signature=\"somesig\"";
        headers.add("Signature", signatureHeader);
        MockServerHttpRequest req = MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        Mono<Boolean> result = httpSignatureService.verifySignature(MockServerWebExchange.from(req), new byte[0]);
        StepVerifier.create(result).expectNext(false).verifyComplete();
    }

    // missing '(request-target)' and 'digest' in headers
    @Test
    void verifySignatureWithMissingRequestTargetOrDigestInSignedHeadersShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader = "keyId=\"" + EXPECTED_KEY_ID + "\",headers=\"host date\",signature=\"somesig\"";
        headers.add("Signature", signatureHeader);
        MockServerHttpRequest req = MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        Mono<Boolean> result = httpSignatureService.verifySignature(MockServerWebExchange.from(req), new byte[0]);
        StepVerifier.create(result).expectNext(false).verifyComplete();
    }

    // missing 'host' in headers for GET request
    @Test
    void verifySignatureWithGETMissingHostInSignedHeadersShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());
        String signatureHeader =
                "keyId=\"" + EXPECTED_KEY_ID + "\",headers=\"(request-target) date\",signature=\"somesig\"";
        headers.add("Signature", signatureHeader);
        MockServerHttpRequest req = MockServerHttpRequest.get(TEST_TARGET_URI.toString()).headers(headers).build();
        Mono<Boolean> result = httpSignatureService.verifySignature(MockServerWebExchange.from(req), new byte[0]);
        StepVerifier.create(result).expectNext(false).verifyComplete();
    }

    // missing 'digest' in headers to verify for POST request
    @Test
    void verifySignatureWithPOSTMissingDigestInSignedHeadersShouldReturnFalse() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.DATE, HttpSignatureService.formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.add(HttpHeaders.HOST, TEST_TARGET_URI.getHost());

        headers.add("Digest", "SHA-256=" +
                Base64.getEncoder().encodeToString(HttpSignature.newSHA256Digest().digest(EXAMPLE_BODY)));
        String signatureHeader =
                "keyId=\"" + EXPECTED_KEY_ID + "\",headers=\"(request-target) host date\",signature=\"somesig\"";
        headers.add("Signature", signatureHeader);
        MockServerHttpRequest req = MockServerHttpRequest.post(TEST_TARGET_URI.toString()).headers(headers).build();
        Mono<Boolean> result = httpSignatureService.verifySignature(MockServerWebExchange.from(req), EXAMPLE_BODY);
        StepVerifier.create(result).expectNext(false).verifyComplete();
    }

    @Test
    void verifySignaturePOSTWithCorrectDigestInHeaderAndBodyShouldReturnTrue() {
        // Prepare signed headers
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        HttpHeaders signedHeaders =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, TEST_TARGET_URI,
                                                          EXAMPLE_BODY).block();
        assertNotNull(String.valueOf(signedHeaders), "Signed headers should not be null");
        assert signedHeaders != null;
        assertTrue(signedHeaders.containsKey("Digest"), "Generated headers must contain Digest for POST");

        // mock Request for verification
        Flux<org.springframework.core.io.buffer.DataBuffer> bodyFluxForVerification =
                Flux.just(dataBufferFactory.wrap(EXAMPLE_BODY));
        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.post(TEST_TARGET_URI.toString()).headers(signedHeaders)
                        .body(bodyFluxForVerification);
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        // mock PublicKeyResolver
        String actualKeyId = HttpSignature.extractKeyId(signedHeaders.getFirst("Signature"));
        assertNotNull(actualKeyId, "Could not extract keyId from generated signature");
        assert HARDCODED_PUBLIC_KEY != null;
        when(publicKeyResolver.resolve(eq(actualKeyId))).thenReturn(Mono.just(HARDCODED_PUBLIC_KEY));

        // verify
        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, EXAMPLE_BODY);
        StepVerifier.create(verificationResultMono).expectNext(true).verifyComplete();
    }

    @Test
    void verifySignaturePOSTWithMismatchedDigestValueInHeaderShouldReturnFalse() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        // generate correct headers first
        HttpHeaders correctlySignedHeaders =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, TEST_TARGET_URI,
                                                          EXAMPLE_BODY).block();
        assertNotNull(String.valueOf(correctlySignedHeaders));

        HttpHeaders tamperedHeaders = new HttpHeaders();
        assert correctlySignedHeaders != null;
        tamperedHeaders.addAll(correctlySignedHeaders);
        tamperedHeaders.set("Digest", "SHA-256=100percentWrongDigestValue"); // Tamper the digest

        Flux<org.springframework.core.io.buffer.DataBuffer> bodyFluxForVerification =
                Flux.just(dataBufferFactory.wrap(EXAMPLE_BODY));
        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.post(TEST_TARGET_URI.toString()).headers(tamperedHeaders)
                        .body(bodyFluxForVerification);
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        String actualKeyId = HttpSignature.extractKeyId(tamperedHeaders.getFirst("Signature"));
        assert HARDCODED_PUBLIC_KEY != null;
        when(publicKeyResolver.resolve(eq(actualKeyId))).thenReturn(Mono.just(HARDCODED_PUBLIC_KEY));

        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, EXAMPLE_BODY);
        StepVerifier.create(verificationResultMono)
                .expectNext(false) // Expect verification to fail due to digest mismatch
                .verifyComplete();
    }

    // combined prepare headers and then verify signature
    @Test
    void prepareHeadersThenVerifySignatureWithPOSTWithBodyHappyPath() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));

        HttpHeaders generatedHeaders =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, TEST_TARGET_URI,
                                                          EXAMPLE_BODY).block();
        assertNotNull(String.valueOf(generatedHeaders), "Generated headers should not be null");

        Flux<byte[]> bodyFluxForVerification = Flux.just(EXAMPLE_BODY);
        assert generatedHeaders != null;
        MockServerHttpRequest mockServerRequest =
                MockServerHttpRequest.post(TEST_TARGET_URI.toString()).headers(generatedHeaders)
                        .body(String.valueOf(bodyFluxForVerification));
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockServerRequest);

        String actualKeyId = HttpSignature.extractKeyId(generatedHeaders.getFirst("Signature"));
        assertNotNull(actualKeyId);
        assert HARDCODED_PUBLIC_KEY != null;
        when(publicKeyResolver.resolve(eq(actualKeyId))).thenReturn(Mono.just(HARDCODED_PUBLIC_KEY));

        Mono<Boolean> verificationResultMono = httpSignatureService.verifySignature(mockExchange, EXAMPLE_BODY);
        StepVerifier.create(verificationResultMono).expectNext(true).verifyComplete();
    }

    @Test
    void prepareSignedHeadersPOSTWithNullBodyShouldSignDigestOfEmptyBody() {
        PubKeyPair keyPair = new PubKeyPair(TEST_ACCOUNT_ID, HARDCODED_PUBLIC_KEY_PEM, HARDCODED_PRIVATE_KEY_PEM);
        when(pubKeyPairRepository.findItemByAcct(TEST_ACCOUNT_ID)).thenReturn(Mono.just(keyPair));
        URI targetUri = URI.create("https://target.example.com/outbox");

        // calculate expected digest for an empty body
        MessageDigest md = HttpSignature.newSHA256Digest();
        byte[] expectedDigestBytes = md.digest(new byte[0]); // Digest of empty body
        String expectedDigestHeaderValue = "sha-256=" + Base64.getEncoder().encodeToString(expectedDigestBytes);

        Mono<HttpHeaders> headersMono =
                httpSignatureService.prepareSignedHeaders(HttpMethod.POST, TEST_ACCOUNT_ID, targetUri,
                                                          null); // Null body

        StepVerifier.create(headersMono).expectNextMatches(headers -> {
            assertTrue(headers.containsKey(HttpHeaders.DATE), "Date header should be present");
            assertEquals("Host header mismatch", targetUri.getHost(), headers.getFirst(HttpHeaders.HOST));
            assertTrue(headers.containsKey(HttpHeaders.ACCEPT), "Accept header should be present");

            assertTrue(headers.containsKey("Digest"), "Digest header should be present for POST with null body");
            assertEquals("Digest value for empty body mismatch", expectedDigestHeaderValue, headers.getFirst("Digest"));

            assertTrue(headers.containsKey("Signature"), "Signature header should be present");
            String sigHeader = headers.getFirst("Signature");
            assertNotNull(sigHeader, "Signature header is null");

            Map<String, String> sigFields = HttpSignature.extractFields(sigHeader);
            String signedHeadersString = sigFields.get("headers");
            assertNotNull(signedHeadersString, "headers field missing in Signature");
            List<String> signedHeadersList = List.of(signedHeadersString.toLowerCase().split(" "));
            // 'digest' should be signed even for a null body in a POST request
            assertTrue(signedHeadersList.containsAll(List.of("(request-target)", "host", "date", "digest")),
                       "Signed headers for POST with null body should include digest");
            return true;
        }).verifyComplete();
    }
}

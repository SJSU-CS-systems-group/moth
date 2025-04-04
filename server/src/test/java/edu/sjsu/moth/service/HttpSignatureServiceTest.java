package edu.sjsu.moth.service;

import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.service.HttpSignatureService;
import edu.sjsu.moth.server.util.MothConfiguration;
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
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimplifiedHttpSignatureServiceTest {

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
    private static final String TEST_ACCOUNT_ID = "testuser";
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
        // grab MothConfiguration from integration test file
        String fullPath = SimplifiedHttpSignatureServiceTest.class.getResource("/test.cfg").getFile();
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
    void signRequest_HappyPath_WithBody_ShouldAddSignature() {
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
                System.out.println(signedRequest.headers().get("Signature"));
                assertTrue(signedRequest.headers().containsKey("Signature"), "Should contain Signature header");
                String sigHeader = signedRequest.headers().getFirst("Signature");
                return sigHeader != null && sigHeader.startsWith("keyId=") && sigHeader.contains("headers=") &&
                        sigHeader.contains("signature=");
            }).verifyComplete();
        }
    }

    @Test
    void signRequest_HappyPath_WithoutBody_ShouldAddSignature() {
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
}

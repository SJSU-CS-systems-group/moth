package edu.sjsu.moth.filter;

import edu.sjsu.moth.server.filter.HttpSignatureWebFilter;
import edu.sjsu.moth.server.service.HttpSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpSignatureWebFilterTest {

    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    @Mock
    private HttpSignatureService mockHttpSignatureService;
    @Mock
    private WebFilterChain mockWebFilterChain;
    @Captor
    private ArgumentCaptor<ServerWebExchange> exchangeCaptor;
    @Captor
    private ArgumentCaptor<byte[]> bodyBytesCaptor;
    private HttpSignatureWebFilter httpSignatureWebFilter;

    @BeforeEach
    void setUp() {
        httpSignatureWebFilter = new HttpSignatureWebFilter(mockHttpSignatureService);
        lenient().when(mockWebFilterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private MockServerWebExchange createExchange(HttpMethod method, String path, HttpHeaders headers, byte[] body) {
        MockServerHttpRequest.BodyBuilder requestBuilder = MockServerHttpRequest.method(method, path).headers(headers);
        MockServerHttpRequest request =
                body != null ? requestBuilder.body(Flux.just(dataBufferFactory.wrap(body))) : requestBuilder.build();
        return MockServerWebExchange.from(request);
    }

    @Test
    void filterGetRequestShouldPassThrough() {
        HttpHeaders headers = new HttpHeaders();
        MockServerWebExchange exchange = createExchange(HttpMethod.GET, "/inbox", headers, null);

        StepVerifier.create(httpSignatureWebFilter.filter(exchange, mockWebFilterChain)).verifyComplete();

        verify(mockWebFilterChain).filter(exchange); // Original exchange should be passed
        verify(mockHttpSignatureService, never()).verifySignature(any(), any());
    }

    @Test
    void filterPostToNonProtectedPathShouldPassThrough() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Signature", "some-signature"); // Even if signature is present
        MockServerWebExchange exchange =
                createExchange(HttpMethod.POST, "/some/other/path", headers, "body".getBytes());

        StepVerifier.create(httpSignatureWebFilter.filter(exchange, mockWebFilterChain)).verifyComplete();

        verify(mockWebFilterChain).filter(exchange);
        verify(mockHttpSignatureService, never()).verifySignature(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/inbox", "/users/testUser/inbox" })
    void filterPostToProtectedPathMissingSignatureHeaderShouldReturnUnauthorized(String protectedPath) {
        HttpHeaders headers = new HttpHeaders();
        MockServerWebExchange exchange = createExchange(HttpMethod.POST, protectedPath, headers, "body".getBytes());

        StepVerifier.create(httpSignatureWebFilter.filter(exchange, mockWebFilterChain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(mockWebFilterChain, never()).filter(any());
        verify(mockHttpSignatureService, never()).verifySignature(any(), any());
    }

    private void testSuccessfulSignatureVerification(String protectedPath, byte[] requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Signature", "valid-signature");
        MockServerWebExchange exchange = createExchange(HttpMethod.POST, protectedPath, headers, requestBody);

        when(mockHttpSignatureService.verifySignature(any(ServerWebExchange.class), eq(requestBody))).thenReturn(
                Mono.just(true));

        StepVerifier.create(httpSignatureWebFilter.filter(exchange, mockWebFilterChain)).verifyComplete();

        verify(mockHttpSignatureService).verifySignature(exchangeCaptor.capture(), bodyBytesCaptor.capture());
        assertArrayEquals(requestBody, bodyBytesCaptor.getValue());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        assertNotNull(capturedExchange);
        assertTrue(capturedExchange.getRequest() instanceof ServerHttpRequestDecorator, "Request should be decorated");

        // verify that the captured exchange has a replayable body
        Mono<byte[]> firstRead = DataBufferUtils.join(capturedExchange.getRequest().getBody()).map(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            return bytes;
        });
        Mono<byte[]> secondRead = DataBufferUtils.join(capturedExchange.getRequest().getBody()).map(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            return bytes;
        });

        StepVerifier.create(firstRead)
                .assertNext(bodyRead -> assertArrayEquals(requestBody, bodyRead, "First body read mismatch"))
                .verifyComplete();
        StepVerifier.create(secondRead).assertNext(
                        bodyRead -> assertArrayEquals(requestBody, bodyRead, "Second body read mismatch (not " +
                                "replayable)"))
                .verifyComplete();

        verify(mockWebFilterChain).filter(capturedExchange);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/inbox", "/users/testUser/inbox" })
    void filterPostToProtectedPathSignatureVerificationSucceedsWithBodyShouldPassThrough(String protectedPath) {
        byte[] requestBody = "{\"type\":\"Create\"}".getBytes(StandardCharsets.UTF_8);
        testSuccessfulSignatureVerification(protectedPath, requestBody);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/inbox", "/users/testUser/inbox" })
    void filterPostToProtectedPathSignatureVerificationSucceedsEmptyBodyShouldPassThrough(String protectedPath) {
        byte[] requestBody = new byte[0]; // Empty body
        testSuccessfulSignatureVerification(protectedPath, requestBody);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/inbox", "/users/testUser/inbox" })
    void filterPostToProtectedPathWithBadSignatureShouldReturnUnauthorized(String protectedPath) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Signature", "invalid-signature");
        byte[] requestBody = "{\"type\":\"Create\"}".getBytes(StandardCharsets.UTF_8);
        MockServerWebExchange exchange = createExchange(HttpMethod.POST, protectedPath, headers, requestBody);

        when(mockHttpSignatureService.verifySignature(any(ServerWebExchange.class), eq(requestBody))).thenReturn(
                Mono.just(false));

        StepVerifier.create(httpSignatureWebFilter.filter(exchange, mockWebFilterChain)).verifyComplete();

        verify(mockHttpSignatureService).verifySignature(exchangeCaptor.capture(), bodyBytesCaptor.capture());
        assertArrayEquals(requestBody, bodyBytesCaptor.getValue());

        assertEquals(HttpStatus.UNAUTHORIZED, exchangeCaptor.getValue().getResponse().getStatusCode());
        verify(mockWebFilterChain, never()).filter(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/inbox", "/users/testUser/inbox" })
    void filterPostToProtectedPathWithServiceFailure(String protectedPath) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Signature", "some-signature");
        byte[] requestBody = "{\"type\":\"Create\"}".getBytes(StandardCharsets.UTF_8);
        MockServerWebExchange exchange = createExchange(HttpMethod.POST, protectedPath, headers, requestBody);

        RuntimeException serviceException = new RuntimeException("Service failure during signature verification");
        when(mockHttpSignatureService.verifySignature(any(ServerWebExchange.class), eq(requestBody))).thenReturn(
                Mono.error(serviceException));

        StepVerifier.create(httpSignatureWebFilter.filter(exchange, mockWebFilterChain))
                .expectErrorMatches(throwable -> throwable == serviceException).verify();

        verify(mockHttpSignatureService).verifySignature(any(ServerWebExchange.class), bodyBytesCaptor.capture());
        assertArrayEquals(requestBody, bodyBytesCaptor.getValue());
        verify(mockWebFilterChain, never()).filter(any());
    }

    @Test
    void filterBypassesSignatureCheckForNonProtectedPaths() {
        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.add("Signature", "some-signature"); // Signature present but path is not protected
        MockServerWebExchange postToNonProtectedPathExchange =
                createExchange(HttpMethod.POST, "/some/other/public/path", postHeaders, "body".getBytes());

        StepVerifier.create(httpSignatureWebFilter.filter(postToNonProtectedPathExchange, mockWebFilterChain))
                .verifyComplete();

        verify(mockWebFilterChain).filter(postToNonProtectedPathExchange);
        verify(mockHttpSignatureService, never()).verifySignature(any(), any());
    }
}

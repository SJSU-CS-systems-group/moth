package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import lombok.extern.apachecommons.CommonsLog;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@CommonsLog
public class HttpSignatureService {
    // https://docs.joinmastodon.org/spec/security/
    private static final List<String> DEFAULT_HEADERS_TO_SIGN_WITH_BODY =
            List.of(HttpSignature.REQUEST_TARGET, "host", "date", "digest");
    private static final List<String> DEFAULT_HEADERS_TO_SIGN_WITHOUT_BODY =
            List.of(HttpSignature.REQUEST_TARGET, "host", "date");

    private final PubKeyPairRepository pubKeyPairRepository;
    private final WebClient webClient;
    private final String serverName;

    @Autowired
    public HttpSignatureService(PubKeyPairRepository pubKeyPairRepository, WebClient.Builder webClientBuilder) {
        this.pubKeyPairRepository = pubKeyPairRepository;
        this.webClient = webClientBuilder.defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        this.serverName = MothConfiguration.mothConfiguration.getServerName();
    }

    public Mono<ClientRequest> signRequest(ClientRequest request, String accountId) {
        return pubKeyPairRepository.findItemByAcct(accountId).flatMap(keyPair -> {
            try {
                // Generate keyId URL (https://docs.joinmastodon.org/spec/activitypub/#publicKey)
                String keyId = "https://" + serverName + "/users/" + accountId +
                        "#main-key"; // TODO : reconstruction, should return the actor document

                // mutable headers (https://stackoverflow.com/questions/53071229/httpclient-what-is-the-difference-between-setheader-and-addheader)
                HttpHeaders headers = HttpHeaders.writableHttpHeaders(request.headers());
                if (!headers.containsKey(HttpHeaders.DATE)) {
                    headers.set(HttpHeaders.DATE, formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
                }
                if (!headers.containsKey(HttpHeaders.HOST)) {
                    headers.set(HttpHeaders.HOST, request.url().getHost());
                }
                // Add Digest header for requests with body
                if (request.body() != null) {
                    // type error fix
                    if (request.body() instanceof Publisher) {
                        return processPublisherBody(request, keyPair, headers, keyId);
                    } else {
                        // For non-Publisher bodies (like BodyInserters), we'll sign without digest
                        return signRequestWithoutBody(request, keyPair, headers, keyId);
                    }
                } else {
                    // No body
                    return signRequestWithoutBody(request, keyPair, headers, keyId);
                }
            } catch (Exception e) {
                log.error("Error during request signing", e);
                return Mono.just(request); // Return original request if process fails
            }
        }).defaultIfEmpty(request); // Return original request if key not found
    }

    private Mono<ClientRequest> processPublisherBody(ClientRequest request,
                                                     edu.sjsu.moth.server.db.PubKeyPair keyPair, HttpHeaders headers,
                                                     String keyId) {

        try {
            return DataBufferUtils.join(
                            (Publisher<? extends DataBuffer>) request.body()) // collecting the body into single buffer
                    .map(dataBuffer -> {
                        try {
                            byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bodyBytes);
                            DataBufferUtils.release(dataBuffer);

                            HttpSignature.addDigest(headers, bodyBytes);

                            PrivateKey privateKey = HttpSignature.pemToPrivateKey(keyPair.privateKeyPEM);

                            // Generate signature
                            String signatureHeader =
                                    HttpSignature.generateSignatureHeader(request.method().name(), request.url(),
                                                                          headers, DEFAULT_HEADERS_TO_SIGN_WITH_BODY,
                                                                          privateKey, keyId);

                            // Create a new request with the signature header
                            ClientRequest.Builder builder = ClientRequest.from(request);
                            headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
                            builder.header("Signature", signatureHeader);

                            // Recreate the body
                            return builder.body((outputMessage, context) -> outputMessage.writeWith(
                                    Mono.just(outputMessage.bufferFactory().wrap(bodyBytes)))).build();
                        } catch (Exception e) {
                            log.error("Failed to process request body", e);
                            return request; // Return original request on error
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to join request body", e);
            return Mono.just(request);
        }
    }

    private Mono<ClientRequest> signRequestWithoutBody(ClientRequest request,
                                                       edu.sjsu.moth.server.db.PubKeyPair keyPair,
                                                       HttpHeaders headers, String keyId) {
        try {
            PrivateKey privateKey = HttpSignature.pemToPrivateKey(keyPair.privateKeyPEM);

            String signatureHeader =
                    HttpSignature.generateSignatureHeader(request.method().name(), request.url(), headers,
                                                          DEFAULT_HEADERS_TO_SIGN_WITHOUT_BODY, privateKey, keyId);

            ClientRequest.Builder builder = ClientRequest.from(request);
            headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
            builder.header("Signature", signatureHeader);

            // Keep the original body
            return Mono.just(builder.build());
        } catch (SignatureException | InvalidKeyException e) {
            log.error("Failed to generate signature for request", e);
            return Mono.just(request); // Return original request if signing fails
        }
    }

    // format date
    // https://stackoverflow.com/questions/45829799/java-time-format-datetimeformatter-rfc-1123-date-time-fails-to-parse-time-zone-n
    // RFC 1123 = HTTP Date Format (https://github.com/mastodon/mastodon/blob/main/app/lib/request.rb)
    private String formatHttpDate(ZonedDateTime dateTime) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime);
        // TODO : lookup for the edge cases (https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html)
        // TODO : move to HttpSignature class?
    }
}

package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import lombok.extern.apachecommons.CommonsLog;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@CommonsLog
public class HttpSignatureService {
    // https://docs.joinmastodon.org/spec/security/
    private static final List<String> DEFAULT_HEADERS_TO_SIGN_WITH_BODY =
            List.of(HttpSignature.REQUEST_TARGET, "host", "date", "digest");
    private static final List<String> DEFAULT_HEADERS_TO_SIGN_WITHOUT_BODY =
            List.of(HttpSignature.REQUEST_TARGET, "host", "date");
    private static final int TIMESTAMP_TOLERANCE_SECONDS = 12 * 60 * 60; // TODO : should be configurable
    private static final long PUBLIC_KEY_CACHE_TTL_SECONDS = 300; // 5 minutes

    private final PubKeyPairRepository pubKeyPairRepository;
    private final WebClient webClient;
    private final String serverName;

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

    // https://github.com/mastodon/mastodon/blob/ff7230df065461ad3fafefdb974f723641059388/app/controllers/concerns/signature_verification.rb
    public Mono<Boolean> verifySignature(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String signatureHeader = headers.getFirst("Signature");

        if (signatureHeader == null) {
            log.warn("No Signature header in request to " + exchange.getRequest().getPath());
            return Mono.just(false);
            // TODO : more error handling?
        }

        try {
            // Split Signature into its separate parameters
            Map<String, String> fields = HttpSignature.extractFields(signatureHeader);
            String keyId = fields.get("keyId");
            String headersToVerify = fields.get("headers");
            String signature = fields.get("signature");

            if (keyId == null || headersToVerify == null || signature == null) {
                log.warn("Incomplete signature header: " + signatureHeader);
                return Mono.just(false);
            }
            if (headersToVerify.contains("date")) {
                String dateHeader = headers.getFirst(HttpHeaders.DATE);
                if (!isDateValid(dateHeader)) {
                    log.warn("Invalid or expired date in request: " + dateHeader);
                    return Mono.just(false);
                }
            }

            // Fetch public key from keyId URL
            return fetchPublicKey(keyId).flatMap(publicKey -> {
                try {
                    // Verify signature
                    boolean isValid = HttpSignature.validateSignatureHeader(exchange.getRequest().getMethod().name(),
                                                                            exchange.getRequest().getURI(), headers,
                                                                            headersToVerify, publicKey, signature);

                    if (!isValid) {
                        log.warn("Invalid signature for request from keyId: " + keyId);
                    }

                    return Mono.just(isValid);
                } catch (Exception e) {
                    log.error("Error verifying signature", e);
                    return Mono.just(false);
                }
            }).defaultIfEmpty(false);
        } catch (Exception e) {
            log.error("Error processing signature header", e);
            return Mono.just(false);
        }
    }

    public Mono<PublicKey> fetchPublicKey(String keyId) {
        // mastodon has separate remote key fetching service (https://github.com/mastodon/mastodon/blob/ff7230df065461ad3fafefdb974f723641059388/app/services/activitypub/fetch_remote_key_service.rb)
        // TODO : Check cache first
        // TODO : check if its local users? optimisation

        // Fetch if not local (keyId -> actor > publicKey > publicKeyPem)
        String actorUrl = keyId.split("#")[0];
        return webClient.get().uri(actorUrl).retrieve().bodyToMono(Map.class).flatMap(actor -> {
            try {
                Map<String, Object> publicKeyObject = (Map<String, Object>) actor.get("publicKey");
                if (publicKeyObject == null) {
                    log.warn("No publicKey in actor document: " + actorUrl);
                    return Mono.empty();
                }

                String publicKeyPem = (String) publicKeyObject.get("publicKeyPem");
                if (publicKeyPem == null) {
                    log.warn("No publicKeyPem in actor document: " + actorUrl);
                    return Mono.empty();
                }
                PublicKey publicKey = HttpSignature.pemToPublicKey(publicKeyPem);

                return Mono.just(publicKey);
            } catch (Exception e) {
                log.error("Error extracting public key from actor", e);
                return Mono.empty();
            }
        }).onErrorResume(e -> {
            log.error("Error fetching actor document: " + actorUrl, e);
            return Mono.empty();
        });
    }

    private boolean isDateValid(String dateHeader) {
        if (dateHeader == null) {
            return false;
        }

        try {
            ZonedDateTime date = ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME); // Parsed
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC); // Current time

            long secondsDifference = Math.abs(ChronoUnit.SECONDS.between(date, now));
            return secondsDifference <= TIMESTAMP_TOLERANCE_SECONDS; // within the past 12 hours
        } catch (Exception e) {
            log.warn("Invalid date format: " + dateHeader, e);
            return false;
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

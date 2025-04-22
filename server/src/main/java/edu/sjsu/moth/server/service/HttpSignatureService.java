package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@CommonsLog
public class HttpSignatureService {
    private static final int TIMESTAMP_TOLERANCE_SECONDS = 12 * 60 * 60; // TODO : should be configurable
    private static final long PUBLIC_KEY_CACHE_TTL_SECONDS = 300; // 5 minutes
    private final PubKeyPairRepository pubKeyPairRepository;
    private final WebClient webClient;
    private final String serverName;
    // https://docs.joinmastodon.org/spec/security/
    private final List<String> headersToSign = List.of(HttpSignature.REQUEST_TARGET, "host", "date");

    public HttpSignatureService(PubKeyPairRepository pubKeyPairRepository, WebClient.Builder webClientBuilder) {
        this.pubKeyPairRepository = pubKeyPairRepository;
        this.webClient = webClientBuilder.defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        this.serverName = MothConfiguration.mothConfiguration.getServerName();
    }

    // format date
    // https://stackoverflow.com/questions/45829799/java-time-format-datetimeformatter-rfc-1123-date-time-fails-to-parse-time-zone-n
    // RFC 1123 = HTTP Date Format (https://github.com/mastodon/mastodon/blob/main/app/lib/request.rb)
    public static String formatHttpDate(ZonedDateTime dateTime) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime);
        // TODO : lookup for the edge cases (https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html)
        // TODO : move to HttpSignature class?
    }

    public Mono<ClientRequest> signRequest(ClientRequest request, String accountId, @Nullable byte[] body) {
        return pubKeyPairRepository.findItemByAcct(accountId).flatMap(keyPair -> {
            try {

                // mutable headers (https://stackoverflow.com/questions/53071229/httpclient-what-is-the-difference-between-setheader-and-addheader)
                HttpHeaders headers = HttpHeaders.writableHttpHeaders(request.headers());
                if (!headers.containsKey(HttpHeaders.DATE)) {
                    headers.set(HttpHeaders.DATE, formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC)));
                }
                if (!headers.containsKey(HttpHeaders.HOST)) {
                    headers.set(HttpHeaders.HOST, request.url().getHost());
                }
                // Add Digest header for requests with body
                List<String> headersToSign = new ArrayList<>(this.headersToSign);
                if (body != null && body.length > 0) {
                    HttpSignature.addDigest(headers, body);
                    headersToSign.add("digest");
                }
                // TODO Add Digest (C3) (recommended)created, (optional) expires
                // TODO algorithm? as metadata?
                PrivateKey privateKey = HttpSignature.pemToPrivateKey(keyPair.privateKeyPEM);
                String keyId = "https://" + serverName + "/users/" + accountId + "#main-key"; //
                String signatureHeader =
                        HttpSignature.generateSignatureHeader(request.method().name(), request.url(), headers,
                                                              headersToSign, privateKey, keyId);
                headers.set("Signature", signatureHeader);

                // build a new ClientRequest with updated headers
                ClientRequest.Builder newRequestBuilder = ClientRequest.from(request).headers(h -> {
                    h.clear();
                    h.addAll(headers);
                });

                ClientRequest signedClientRequest = newRequestBuilder.build();
                return Mono.just(signedClientRequest);
            } catch (Exception e) {
                log.error("Error during request signing", e);
                return Mono.just(request); // Return original request if process fails
            }
        }).defaultIfEmpty(request); // Return original request if key not found
    }

    // https://github.com/mastodon/mastodon/blob/ff7230df065461ad3fafefdb974f723641059388/app/controllers/concerns/signature_verification.rb
    public Mono<Boolean> verifySignature(ServerWebExchange exchange) {
        // TODO : Caching, Domain Blocking, Local URI Checks, Rate Limiting, retry if key fetching fails.
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String signatureHeader = headers.getFirst("Signature");

        if (signatureHeader == null) {
            log.warn("No Signature header in request to " + exchange.getRequest().getPath());
            return Mono.just(false);
            // TODO : more error handling?
        }

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

        // signature strength checks - https://github.com/mastodon/mastodon/blob/main/app/controllers/concerns/signature_verification.rb#L116C1-L120C180
        List<String> headersToVerifyList = Arrays.asList(headersToVerify.toLowerCase().split(" "));

        //      rule 1 Requires Date or (created)
        if (!(headersToVerifyList.contains("date") || headersToVerifyList.contains("(created)"))) {
            // currently we are only signing date, might need adjustment later if we support created
            log.warn("Signature verification failed: Moth requires Date or (created) header to be signed. Signed " +
                             "headers: " + headersToVerify);
            return Mono.just(false);
        }

        //      rule 2 Requires (request-target) or Digest
        if (!(headersToVerifyList.contains(HttpSignature.REQUEST_TARGET) || headersToVerifyList.contains("digest"))) {
            log.warn("Signature verification failed: Moth requires (request-target) or Digest header to be signed." +
                             " Signed headers: " + headersToVerify);
            return Mono.just(false);
        }

        //      rule 3 Requires Host for GET
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.GET && !headersToVerifyList.contains("host")) {
            log.warn("Signature verification failed: Moth requires Host header to be signed for GET. Signed " +
                             "headers: " + headersToVerify);
            return Mono.just(false);
        }

        //      rule 4 Requires Digest for POST
        if (method == HttpMethod.POST && !headersToVerifyList.contains("digest")) {
            log.warn("Signature verification failed: Moth requires Digest header to be signed for POST. Signed " +
                             "headers: " + headersToVerify);
            return Mono.just(false);
        }

        // Date verification
        if (headersToVerifyList.contains("date")) {
            String dateHeader = headers.getFirst(HttpHeaders.DATE);
            if (!isDateValid(dateHeader)) {
                log.warn("Invalid or outside tolerance Date header in request: " + dateHeader);
                return Mono.just(false);
            }
        }

        // TODO : checks for the header verification if (created) (expires) present, might need to change utility
        //  function

        // Digest verification
        // https://github.com/mastodon/mastodon/blob/main/app/controllers/concerns/signature_verification.rb#L123C7-L123C25
        Mono<Boolean> digestCheckResult = null;
        if (headersToVerifyList.contains("digest")) {
            String digestHeaderValue = headers.getFirst("Digest");
            if (digestHeaderValue == null || digestHeaderValue.isBlank()) {
                log.warn("Digest header signed but missing/empty in request.");
                digestCheckResult = Mono.just(false);
            } else {
                digestCheckResult = checkRequestDigest(exchange, headers);
            }
        } else {
            digestCheckResult = Mono.just(true);
        }

        return digestCheckResult.filter(Boolean::booleanValue).switchIfEmpty(Mono.defer(() -> {
                    log.warn("Digest verification failed or header mismatch.");
                    return Mono.just(false);
                })).flatMap(ignored -> fetchPublicKey(keyId)) // digest verified, good to go for the signature
                .flatMap(publicKey -> {
                    try {
                        boolean isValid =
                                HttpSignature.validateSignatureHeader(exchange.getRequest().getMethod().name(),
                                                                      exchange.getRequest().getURI(), headers,
                                                                      headersToVerify, publicKey, signature);

                        if (!isValid) {
                            log.warn("Invalid signature value for request from keyId: " + keyId);
                        }
                        return Mono.just(isValid);
                    } catch (Exception e) {
                        log.error("Error during signature validation for keyId: " + keyId, e);
                        return Mono.just(false);
                    }
                }).defaultIfEmpty(false);
    }

    private Mono<Boolean> checkRequestDigest(ServerWebExchange exchange, HttpHeaders headers) {
        // ONLY "SHA-256=value", might need to add
        String digestHeaderValue = headers.getFirst("Digest");
        String expectedValueBase64 = null;
        String[] parts = digestHeaderValue.trim().split("=", 2); // Split into max 2 parts

        // Check format and algorithm in digest header
        if (parts.length == 2 && "sha-256".equalsIgnoreCase(parts[0].trim())) {
            expectedValueBase64 = parts[1].trim();
        } else {
            log.warn("Digest header is not in the expected 'SHA-256=value' format. Received: " + digestHeaderValue);
            return Mono.just(false); // Fail if not exactly SHA-256=value
        }

        // Base64 validity and SHA-256 length (32 bytes)
        byte[] decodedDigestBytes;
        try {
            decodedDigestBytes = Base64.getDecoder().decode(expectedValueBase64);
            if (decodedDigestBytes.length != 32) {
                log.warn("Invalid Digest value. Decoded value length is not 32 bytes (SHA-256). Header value: " +
                                 expectedValueBase64);
                return Mono.just(false);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Digest value. Not a valid Base64 string. Header value: " + expectedValueBase64);
            return Mono.just(false);
        }

        final String finalExpectedValueBase64 = expectedValueBase64;

        // calculate the actual digest
        return DataBufferUtils.join(exchange.getRequest().getBody()).map(dataBuffer -> {
            byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bodyBytes);
            DataBufferUtils.release(dataBuffer);
            return bodyBytes;
        }).defaultIfEmpty(new byte[0]).<Boolean>handle((bodyBytes, sink) -> {
            MessageDigest sha256 = HttpSignature.newSHA256Digest();
            if (sha256 == null) {
                sink.error(new NoSuchAlgorithmException("SHA-256 MessageDigest unavailable"));
                return;
            }

            byte[] actualDigestBytes = sha256.digest(bodyBytes);
            String actualDigestBase64 = Base64.getEncoder().encodeToString(actualDigestBytes);

            // 7. Compare
            boolean match = Objects.equals(actualDigestBase64, finalExpectedValueBase64);
            if (!match) {
                log.warn("Digest mismatch. Header: SHA-256=" + finalExpectedValueBase64 + ", Calculated: SHA-256=" +
                                 actualDigestBase64);
            } else {
                log.trace("Digest matched.");
            }
            sink.next(match);
        }).onErrorResume(e -> {
            log.error("Error calculating request body digest", e);
            return Mono.just(false);
        });
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
}

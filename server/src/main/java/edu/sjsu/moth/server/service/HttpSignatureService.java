package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.keyManager.PublicKeyResolver;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.HttpSignature;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@CommonsLog
public class HttpSignatureService {
    private static final int TIMESTAMP_TOLERANCE_SECONDS = 12 * 60 * 60; // TODO : should be configurable
    private static final int SHA256_DIGEST_LENGTH_BYTES = 32;
    private static final DateTimeFormatter RFC_1123_COMPLIANT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private final PubKeyPairRepository pubKeyPairRepository;
    private final String serverName;
    // https://docs.joinmastodon.org/spec/security/
    private final List<String> baseHeadersToSign = List.of(HttpSignature.REQUEST_TARGET, "host", "date");
    private final PublicKeyResolver keyResolver;

    public HttpSignatureService(PubKeyPairRepository pubKeyPairRepository, PublicKeyResolver keyResolver) {
        this.pubKeyPairRepository = pubKeyPairRepository;
        this.keyResolver = keyResolver;
        this.serverName = MothConfiguration.mothConfiguration.getServerName();
    }

    // format date
    // https://stackoverflow.com/questions/45829799/java-time-format-datetimeformatter-rfc-1123-date-time-fails-to-parse-time-zone-n
    // RFC 1123 = HTTP Date Format (https://github.com/mastodon/mastodon/blob/main/app/lib/request.rb)
    public static String formatHttpDate(ZonedDateTime dateTime) {
        return RFC_1123_COMPLIANT_FORMATTER.format(dateTime);
    }

    public Mono<HttpHeaders> prepareSignedHeaders(HttpMethod method, String sendingActorId, URI targetUri,
                                                  byte[] bodyBytes) {
        return pubKeyPairRepository.findItemByAcct(sendingActorId).switchIfEmpty(
                        Mono.error(() -> new RuntimeException("Private key not found for actor: " + sendingActorId)))
                .handle((keyPair, sink) -> {
                    try {
                        PrivateKey privateKey = HttpSignature.pemToPrivateKey(keyPair.privateKeyPEM);
                        String keyId = "https://" + serverName + "/users/" + sendingActorId + "#main-key";
                        String targetHost = targetUri.getHost();
                        if (targetHost == null) {
                            sink.error(new IllegalArgumentException("Invalid target URI host in " + targetUri));
                            return;
                        }

                        HttpHeaders headers = new HttpHeaders();
                        String httpDate = formatHttpDate(ZonedDateTime.now(ZoneOffset.UTC));
                        headers.setDate(ZonedDateTime.parse(httpDate, RFC_1123_COMPLIANT_FORMATTER));
                        headers.set(HttpHeaders.HOST, targetHost);
                        headers.setAccept(List.of(MediaType.valueOf("application/activity+json"), MediaType.valueOf(
                                "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"")));
                        List<String> headersToSign = new ArrayList<>(baseHeadersToSign); // Start with base
                        if (method == HttpMethod.POST) {
                            headers.setContentType(MediaType.valueOf(
                                    "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\""));
                            HttpSignature.addDigest(headers, bodyBytes != null ? bodyBytes : new byte[0]);
                            headersToSign.add("digest");
                        }
                        String signatureHeaderValue =
                                HttpSignature.generateSignatureHeader(method.name(), targetUri, headers, headersToSign,
                                                                      privateKey, keyId);
                        headers.set("Signature", signatureHeaderValue);
                        sink.next(headers);
                    } catch (Exception e) {
                        sink.error(new RuntimeException("Failed to prepare signed headers", e));
                    }
                });
    }

    // https://github.com/mastodon/mastodon/blob/ff7230df065461ad3fafefdb974f723641059388/app/controllers/concerns/signature_verification.rb
    public Mono<Boolean> verifySignature(ServerWebExchange exchange, byte[] bodyBytes) {
        // TODO : Domain Blocking, Local URI Checks
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String signatureHeader = headers.getFirst("Signature");

        if (signatureHeader == null) {
            log.warn("No Signature header in request to " + exchange.getRequest().getPath());
            return Mono.just(false);
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
        Mono<Boolean> digestCheckResult;
        if (headersToVerifyList.contains("digest")) {
            String digestHeaderValue = headers.getFirst("Digest");
            digestCheckResult = checkRequestDigest(digestHeaderValue, bodyBytes);
        } else {
            digestCheckResult = Mono.just(true);
        }

        return digestCheckResult.filter(Boolean::booleanValue).switchIfEmpty(Mono.defer(() -> {
                    log.warn("Digest verification failed or header mismatch.");
                    return Mono.just(false);
                })).flatMap(ignored -> keyResolver.resolve(keyId)) // digest verified, good to go for the signature
                .flatMap(publicKey -> {
                    try {
                        log.info("Validating signature for keyId: " + keyId);
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

    private Mono<Boolean> checkRequestDigest(String digestHeaderValue, @Nullable byte[] bytes) {
        String expectedValueBase64;
        if (digestHeaderValue == null) return Mono.just(false);
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
            if (decodedDigestBytes.length != SHA256_DIGEST_LENGTH_BYTES) {
                log.warn("Invalid Digest value. Decoded value length is not 32 bytes (SHA-256). Header value: " +
                                 expectedValueBase64);
                return Mono.just(false);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Digest value. Not a valid Base64 string. Header value: " + expectedValueBase64);
            return Mono.just(false);
        }

        try {
            MessageDigest sha256 = HttpSignature.newSHA256Digest();
            if (sha256 == null) {
                log.error("SHA-256 MessageDigest algorithm not available.");
                return Mono.just(false);
            }

            byte[] bodyToDigest = (bytes != null) ? bytes : new byte[0];
            byte[] actualDigestBytes = sha256.digest(bodyToDigest);

            String actualDigestBase64 = Base64.getEncoder().encodeToString(actualDigestBytes);
            boolean match = Objects.equals(actualDigestBase64, expectedValueBase64);
            if (!match) {
                log.warn("Digest mismatch. Header: SHA-256=" + expectedValueBase64 + ", Calculated: SHA-256=" +
                                 actualDigestBase64);
            } else {
                log.trace("Digest matched.");
            }
            return Mono.just(match);
        } catch (Exception e) {
            log.error("Error calculating request body digest", e);
            return Mono.just(false);
        }
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

package edu.sjsu.moth.util;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * routines to support HTTP Signatures
 *
 * for more info see:
 * https://docs.joinmastodon.org/spec/security/
 * https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures
 *
 */

@CommonsLog
public class HttpSignature {
    public static final String REQUEST_TARGET = "(request-target)";
    static public final Pattern HTTP_HEADER_FIELDS_PATTERN =
            Pattern.compile("(?<key>\\p{Alnum}+)=\"(?<value>([^\"])*)\"");
    static public final Pattern KEY_ID_PATTERN = Pattern.compile("keyId=\"([^\"]+)\"");
    static public final Pattern SIGNATURE_PATTERN = Pattern.compile("signature=\"([^\"]+)\"");

    public static Signature newSigner() {
        try {
            return Signature.getInstance("SHA256withRSA");
        } catch (NoSuchAlgorithmException e) {
            log.fatal(e.getMessage(), e);
            System.exit(100);
            return null; // make compiler happy...
        }
    }

    public static MessageDigest newSHA256Digest() {
        try {
            return MessageDigest.getInstance("sha-256");
        } catch (NoSuchAlgorithmException e) {
            log.fatal(e.getMessage(), e);
            System.exit(100);
            return null; // make compiler happy
        }
    }

    public static WebClient.Builder signHeaders(WebClient.Builder clientBuilder, List<String> headers,
                                                PrivateKey signingKey, String keyUri) {
        clientBuilder.filter((request, next) -> {
            try {
                String sigLine =
                        generateSignatureHeader(request.method().name(), request.url(), request.headers(), headers,
                                                signingKey, keyUri);
                //Cannot update an existing request once it is created to add a new header, so creating a new request
                ClientRequest newRequest =
                        ClientRequest.from(request).headers(h -> h.add("Signature", sigLine)).build();

                return next.exchange(newRequest);

            } catch (InvalidKeyException | SignatureException e) {
                log.error("couldn't sign request", e);
                return next.exchange(request); // fallback without signature
            }
        });
        return clientBuilder;
    }

    public static String generateSignatureHeader(String requestMethod, URI requestURI, HttpHeaders requestHeaders,
                                                 List<String> headers, PrivateKey signingKey, String keyUri) throws SignatureException, InvalidKeyException {
        var toSign = generateHeadersToSign(requestMethod, requestURI, requestHeaders, headers);
        var signer = newSigner();
        signer.initSign(signingKey);
        signer.update(toSign);
        var signature = Base64.getEncoder().encodeToString(signer.sign());
        return "keyId=\"%s\",headers=\"%s\",signature=\"%s\"".formatted(keyUri, String.join(" ", headers), signature);
    }

    private static byte[] generateHeadersToSign(String requestMethod, URI requestURI, HttpHeaders requestHeaders,
                                                List<String> headers) {
        var toSign = headers.stream().map(h -> {
            if (h.equalsIgnoreCase(REQUEST_TARGET)) {
                var uri = requestURI;
                var rawPath = uri.getRawPath();
                if (uri.getRawQuery() != null) {
                    rawPath += "?" + uri.getRawQuery();
                }
                return "%s: %s %s".formatted(REQUEST_TARGET, requestMethod.toLowerCase(), rawPath);
            } else {
                return h + ": " + String.join(",", requestHeaders.getOrDefault(h, List.of()));
            }
        }).collect(Collectors.joining("\n"));
        return toSign.getBytes();
    }

    public static boolean validateSignatureHeader(String method, URI uri, HttpHeaders headers, String signedHeaders,
                                                  PublicKey publicKey, String signature) throws InvalidKeyException,
            SignatureException {
        var toValidate = generateHeadersToSign(method, uri, headers, List.of(signedHeaders.split(" ")));
        var signer = newSigner();
        String sigLine = null;
        signer.initVerify(publicKey);
        signer.update(toValidate);
        return signer.verify(Base64.getMimeDecoder().decode(signature));

    }

    public static PublicKey pemToPublicKey(String publicKeyPEM) {
        var s = publicKeyPEM.replaceAll("---+[^-]+---+", "").replace("\n", "");
        var spec = new X509EncodedKeySpec(Base64.getDecoder().decode(s));
        try {
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            log.fatal("RSA key factory not available!", e);
            return null;
        }
    }

    public static PrivateKey pemToPrivateKey(String privateKeyPEM) {
        var s = privateKeyPEM.replaceAll("---+[^-]+---+", "").replace("\n", "");
        var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(s));
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            log.fatal("RSA key factory error!", e);
            return null;
        }
    }

    public static Map<String, String> extractFields(String sig) {
        var match = HTTP_HEADER_FIELDS_PATTERN.matcher(sig);
        var map = new HashMap<String, String>();
        while (match.find()) {
            map.put(match.group("key"), match.group("value"));
        }
        return map;
    }

    public static String extractKeyId(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        var matcher = KEY_ID_PATTERN.matcher(headerValue);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public static String extractSignature(String headerValue) {
        Matcher matcher = SIGNATURE_PATTERN.matcher(headerValue);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static void addDigest(HttpHeaders headers, byte[] body) {
        // https://docs.joinmastodon.org/spec/security/ says we should use SHA-256
        headers.add("Digest",
                    "sha-256=%s".formatted(Base64.getMimeEncoder().encodeToString(newSHA256Digest().digest(body))));
    }
}

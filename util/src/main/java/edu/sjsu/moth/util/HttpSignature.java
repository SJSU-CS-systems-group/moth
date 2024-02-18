package edu.sjsu.moth.util;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.apachecommons.CommonsLog;

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
    private HttpSignature() {
        throw new IllegalStateException("This is a utility class. You shouldn't instantiate it.");
    }

    public static final String REQUEST_TARGET = "(request-target)";
    public static final Pattern HTTP_HEADER_FIELDS_PATTERN = Pattern.compile(
            "(?<key>\\p{Alnum}+)=\"(?<value>([^\"])*)\"");

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
                String sigLine = generateSignatureHeader(request.method().name(), request.url(), request.headers(),
                        headers, signingKey, keyUri);
                request.headers().add("Signature", sigLine);
            } catch (InvalidKeyException | SignatureException e) {
                log.error("couldn't sign request", e);
            }
            return next.exchange(request);
        });

        return clientBuilder;
    }

    static String generateSignatureHeader(String requestMethod, URI requestURI, HttpHeaders requestHeaders,
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

    public static void addDigest(HttpHeaders headers, byte[] body) {
        // https://docs.joinmastodon.org/spec/security/ says we should use SHA-256
        headers.add("Digest",
                "sha-256=%s".formatted(Base64.getMimeEncoder().encodeToString(newSHA256Digest().digest(body))));
    }
}

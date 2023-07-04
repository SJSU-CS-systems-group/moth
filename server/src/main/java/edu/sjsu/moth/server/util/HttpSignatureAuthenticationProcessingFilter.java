package edu.sjsu.moth.server.util;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.match;
import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.notMatch;

/**
 * WebFilter support for HTTP Signatures
 * <p>
 * for more info see:
 * https://docs.joinmastodon.org/spec/security/
 * https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures
 * <p>
 * If an HTTP Signature is found, it will inject an HttpSignatureAuthentication representing
 * the signature.
 * It relies on Digest header processing to be done by another filter.
 */
@CommonsLog
public class HttpSignatureAuthenticationProcessingFilter extends AuthenticationWebFilter {

    public static final String SIGNATURE_HEADER = "signature";

    public HttpSignatureAuthenticationProcessingFilter(ReactiveAuthenticationManager authenticationManager) {
        super(authenticationManager);
        setRequiresAuthenticationMatcher(this::httpSignatureAuthenticationMatcher);
        setServerAuthenticationConverter(this::authenticationConverter);
    }

    // we sign the selected headers lower cased and joined with a newline
    private static String constructSignedHeader(ServerWebExchange exchange, HashMap<String, String> creds) {
        var sb = new StringBuilder();
        for (var header : creds.get("headers").split(" ")) {
            if (sb.length() > 0) {sb.append("\n");}
            if (header.equalsIgnoreCase("(request-target)")) {
                var uri = exchange.getRequest().getURI();
                var rawPath = uri.getRawPath();
                if (uri.getQuery() != null) {
                    rawPath += "?" + uri.getRawQuery();
                }
                sb.append("(request-target): %s %s".formatted(exchange.getRequest().getMethod().name().toLowerCase(),
                                                              rawPath));
            } else {
                sb.append(header).append(": ").append(String.join(",", exchange.getRequest().getHeaders().getOrDefault(header, List.of())));
            }
        }
        return sb.toString();
    }

    private static HashMap<String, String> getSignatureCreds(List<String> signatureHeaders) {
        // extract the key elements of the signatureHeaders
        var creds = new HashMap<String, String>();
        for (var h : signatureHeaders) {
            // this splitting is error-prone! it should be okay for signature, but we should
            // have a better splitter (keyId="http://this.com/is/a/bad,url",signature="thiswillbethe3rdpart")
            for (var he : h.split(",")) {
                var parts = he.split("=", 2);
                if (parts.length != 2) continue;
                creds.put(parts[0].toLowerCase(), Util.stripQuotes(parts[1]));
            }
        }
        return creds;
    }

    private Mono<ServerWebExchangeMatcher.MatchResult> httpSignatureAuthenticationMatcher(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().get(SIGNATURE_HEADER) != null ? match() : notMatch();
    }

    // we have a few steps to follow:
    // 1. get the key properties from the signature header:
    //     keyid - where we get the public key
    //     headers - the headers that were signed
    //     signature - the signature over those headers
    // 2. if there is a digest header, we expect the DigestFilter to process it (TODO)
    // 3. fetch the signing key based on the key id and parse the key out of
    //    the resulting JSON
    // 4. concatenate the headers that need to be signed
    // 5. verify the signature
    private Mono<Authentication> authenticationConverter(ServerWebExchange exchange) {
        var signatureHeaders = exchange.getRequest().getHeaders().get(SIGNATURE_HEADER);
        if (signatureHeaders == null) return Mono.empty();

        HashMap<String, String> creds = getSignatureCreds(signatureHeaders);
        if (!creds.containsKey("keyid") || !creds.containsKey("headers") || !creds.containsKey("signature")) {
            return Mono.error(new SignatureException("key components missing from " + signatureHeaders + " " + creds));
        }

        return FIFOCacher.getFIFOCacher().fetchPEM(creds.get("keyid")).flatMap(pub -> {
                    var signedHeader = constructSignedHeader(exchange, creds);
                    try {
                        var sig = Signature.getInstance("SHA256withRSA");
                        sig.initVerify(pub);
                        sig.update(signedHeader.getBytes(StandardCharsets.UTF_8));
                        return sig.verify(Base64.getDecoder().decode(creds.get("signature"))) ? Mono.just((Authentication) new HttpSignatureAuthentication(creds)) : Mono.error(
                                new SignatureException("bad signature"));
                    } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
                        log.error("Problem verifying signature: " + e);
                        return Mono.error(e);
                    }
                })
                // map errors to empty so that we skip authentication
                .onErrorResume(t -> {
                    log.info("cannot validate signature for %s: %s".formatted(creds.get("keyid"), t.getMessage()));
                    return Mono.empty();
                });
    }
}
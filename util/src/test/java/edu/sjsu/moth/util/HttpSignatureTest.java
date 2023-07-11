package edu.sjsu.moth.util;

import lombok.extern.apachecommons.CommonsLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMapAdapter;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.List;
import java.util.Map;

/**
 * tests from https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures-12#appendix-C.1
 */
@CommonsLog
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HttpSignatureTest {

    public static final String PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCFENGw33yGihy92pDjZQhl0C3
            6rPJj+CvfSC8+q28hxA161QFNUd13wuCTUcq0Qd2qsBe/2hFyc2DCJJg0h1L78+6
            Z4UMR7EOcpfdUE9Hf3m/hs+FUR45uBJeDK1HSFHD8bHKD6kv8FPGfJTotc+2xjJw
            oYi+1hqp1fIekaxsyQIDAQAB
            -----END PUBLIC KEY-----
            """;
    public static final PublicKey PUBLIC_KEY = HttpSignature.pemToPublicKey(PUBLIC_KEY_PEM);

    public static final String PRIVATE_KEY_PEM = """
            -----BEGIN RSA PRIVATE KEY-----
            MIICXgIBAAKBgQDCFENGw33yGihy92pDjZQhl0C36rPJj+CvfSC8+q28hxA161QF
            NUd13wuCTUcq0Qd2qsBe/2hFyc2DCJJg0h1L78+6Z4UMR7EOcpfdUE9Hf3m/hs+F
            UR45uBJeDK1HSFHD8bHKD6kv8FPGfJTotc+2xjJwoYi+1hqp1fIekaxsyQIDAQAB
            AoGBAJR8ZkCUvx5kzv+utdl7T5MnordT1TvoXXJGXK7ZZ+UuvMNUCdN2QPc4sBiA
            QWvLw1cSKt5DsKZ8UETpYPy8pPYnnDEz2dDYiaew9+xEpubyeW2oH4Zx71wqBtOK
            kqwrXa/pzdpiucRRjk6vE6YY7EBBs/g7uanVpGibOVAEsqH1AkEA7DkjVH28WDUg
            f1nqvfn2Kj6CT7nIcE3jGJsZZ7zlZmBmHFDONMLUrXR/Zm3pR5m0tCmBqa5RK95u
            412jt1dPIwJBANJT3v8pnkth48bQo/fKel6uEYyboRtA5/uHuHkZ6FQF7OUkGogc
            mSJluOdc5t6hI1VsLn0QZEjQZMEOWr+wKSMCQQCC4kXJEsHAve77oP6HtG/IiEn7
            kpyUXRNvFsDE0czpJJBvL/aRFUJxuRK91jhjC68sA7NsKMGg5OXb5I5Jj36xAkEA
            gIT7aFOYBFwGgQAQkWNKLvySgKbAZRTeLBacpHMuQdl1DfdntvAyqpAZ0lY0RKmW
            G6aFKaqQfOXKCyWoUiVknQJAXrlgySFci/2ueKlIE1QqIiLSZ8V8OlpFLRnb1pzI
            7U1yQXnTAEFYM560yJlzUpOb1V4cScGd365tiSMvxLOvTA==
            -----END RSA PRIVATE KEY-----
            """;

    /* converted PRIVATE_KEY_PEM to PRIVATE_RSA_KEY_PEM using openssl pkey  */
    public static final String PRIVATE_RSA_KEY_PEM = """
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

    public static final PrivateKey PRIVATE_KEY = HttpSignature.pemToPrivateKey(PRIVATE_RSA_KEY_PEM);

    /*
     * example request from spec:
     *  POST /foo?param=value&pet=dog HTTP/1.1
     * Host: example.com
     * Date: Sun, 05 Jan 2014 21:31:40 GMT
     * Content-Type: application/json
     * Digest: SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
     * Content-Length: 18
     *
     *{"hello": "world"}
     */
    public static final URI EXAMPLE_URI = URI.create("/foo?param=value&pet=dog");
    public static final HttpHeaders EXAMPLE_HEADERS = new HttpHeaders(new MultiValueMapAdapter<>(
            Map.of("host", List.of("example.com"), "date", List.of("Sun, 05 Jan 2014 21:31:40 GMT"), "content-Type",
                   List.of("application/json"), "digest",
                   List.of("SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE="), "content-Length", List.of("18"))));

    public static final byte[] EXAMPLE_BODY = "{\"hello\": \"world\"}".getBytes();

    @Test
    void testBoth() throws SignatureException, InvalidKeyException {
        var sig = HttpSignature.generateSignatureHeader("POST", EXAMPLE_URI, EXAMPLE_HEADERS,
                                                        List.of(HttpSignature.REQUEST_TARGET, "host", "date"),
                                                        PRIVATE_KEY, "Test");
        var fields = HttpSignature.extractFields(sig);
        Assertions.assertTrue(
                HttpSignature.validateSignatureHeader("POST", EXAMPLE_URI, EXAMPLE_HEADERS, fields.get("headers"),
                                                      PUBLIC_KEY, fields.get("signature")));
    }

    @Test
    void testHttpSigning() throws SignatureException, InvalidKeyException {
        /* C2 basic test */
        /*    NOTE: mastodon drops the algorithm field! */
        var expected = """
                keyId="Test",headers="(request-target) host date",signature="qdx+H7PHHDZgy4y/Ahn9Tny9V3GP6YgBPyUXMmoxWtLbHpUnXS2mg2+SbrQDMCJypxBLSPQR2aAjn7ndmw2iicw3HMbe8VfEdKFYRqzic+efkb3nndiv/x1xSHDJWeSWkx3ButlYSuBskLu6kd9Fswtemr3lgdDEmn04swr2Os0=\"""";
        var actual = HttpSignature.generateSignatureHeader("POST", EXAMPLE_URI, EXAMPLE_HEADERS,
                                                           List.of(HttpSignature.REQUEST_TARGET, "host", "date"),
                                                           PRIVATE_KEY, "Test");
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testHttpValidatingC2() throws SignatureException, InvalidKeyException {
        /* C2. basic test */
        /*     NOTE: mastodon drops the algorithm field! */
        var validated = HttpSignature.validateSignatureHeader("POST", EXAMPLE_URI, EXAMPLE_HEADERS,
                                                              "(request-target) host date", PUBLIC_KEY, """
                                                                      qdx+H7PHHDZgy4y/Ahn9Tny9V3GP6YgBPyUXMmoxWtLbHpUnXS2mg2+SbrQDMCJypxBLSPQR2aAjn7ndmw2iicw3HMbe8VfEdKFYRqzic+efkb3nndiv/x1xSHDJWeSWkx3ButlYSuBskLu6kd9Fswtemr3lgdDEmn04swr2Os0=
                                                                      """);
        Assertions.assertTrue(validated);
    }

    @Test
    void testHttpDigest() {
        HttpHeaders headers = new HttpHeaders();
        HttpSignature.addDigest(headers, EXAMPLE_BODY);
        // NOTE: we skip the first three characters because the mastodon documentation uses lowercase "sha",
        //       which is what HttpSignature implemented, but the example uses uppercase "SHA".
        Assertions.assertEquals(EXAMPLE_HEADERS.get("digest").get(0).substring(3),
                                headers.get("digest").get(0).substring(3));
    }
}

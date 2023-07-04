package edu.sjsu.moth.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.xml.crypto.URIReferenceException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class WebFingerUtils {

    public static final Map<String, String> CONTENT_TYPE_HEADERS = Map.of("Content-type",
                                                                          MothMimeType.APPLICATION_ACTIVITY_VALUE,
                                                                          "Accept",
                                                                          MothMimeType.APPLICATION_ACTIVITY_VALUE);
    static private final Pattern USER_URL_PATTERN = Pattern.compile("https://([^/]+).*/([^/]+)");

    static private <T> Mono<T> createGetClient(String uri, Map<String, String> headers, Class<T> clazz) {
        var client = WebClient.create().get().uri(uri);
        if (headers != null) headers.forEach(client::header);
        return client.retrieve().bodyToMono(clazz);
    }

    static public Mono<WebFinger> finger(String user, String host) {
        var uri = MessageFormat.format("https://{1}/.well-known/webfinger?resource=acct:{0}@{1}", user, host);
        return createGetClient(uri, CONTENT_TYPE_HEADERS, WebFinger.class);
    }

    static public Mono<FingerAndAccount> resolve(String userUrl) {
        WebFinger finger = null;
        var match = USER_URL_PATTERN.matcher(userUrl);
        if (!match.find()) {
            Mono.error(new URIReferenceException(String.format(userUrl, USER_URL_PATTERN.pattern())));
        }
        return finger(match.group(2), match.group(1)).flatMap(
                f -> createGetClient(userUrl, CONTENT_TYPE_HEADERS, JsonNode.class).map(
                        a -> new FingerAndAccount(f, a)));
    }

    private static String PEMEncode(byte[] bytes, String armorLabel) {
        return "-----BEGIN " + armorLabel + "-----\n" + Base64.getMimeEncoder(72, new byte[] { '\n' })
                .encodeToString(bytes) + "\n-----END " + armorLabel + "-----\n";
    }

    public static PubPrivKeyPEM genPubPrivKeyPem() {
        try {
            var keyGenerator = KeyPairGenerator.getInstance("RSA");
            keyGenerator.initialize(2048);
            var pair = keyGenerator.generateKeyPair();
            return new PubPrivKeyPEM(PEMEncode(pair.getPublic().getEncoded(), "PUBLIC KEY"),
                                     PEMEncode(pair.getPrivate().getEncoded(), "PRIVATE KEY"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record ProfileEndpoints(String sharedInbox) {}

    @JsonPropertyOrder({ "id", "owner", "publicKeyPem" })
    public record PublicKeyMessage(String owner, String publicKeyPem) {
        public String getId() {
            return owner + "#main-key";
        }
    }

    /**
     * simple helper class that will get serialize by jackson to a string value
     */
    static protected class StringType {
        final private String str;

        StringType(String str) {this.str = str;}

        @JsonValue
        public String toString() {return str;}
    }

    static public class RelType extends StringType {
        static public final RelType SELF = new RelType("self");
        static public final RelType PROFILE = new RelType("http://webfinger.net/rel/profile-page");

        private RelType(String relType) {super(relType);}

        @JsonCreator()
        static public RelType getInstance(String relType) {
            if (relType.equals(SELF.toString())) {
                return SELF;
            } else if (relType.equals(PROFILE.toString())) {
                return PROFILE;
            } else {
                return new RelType(relType);
            }
        }
    }

    static public class MothMimeType {
        static public final MediaType APPLICATION_ACTIVITY = new MediaType("application", "activity+json");
        static public final String APPLICATION_ACTIVITY_VALUE = "application/activity+json";
    }

    /**
     * Structure representing the type of a webfinger link
     */
    public record FingerLink(RelType rel, String type, String href) {}

    /**
     * Structure returned by a webfinger request
     */
    public record WebFinger(String subject, List<String> aliases, List<FingerLink> links) {}

    public record FingerAndAccount(WebFinger finger, JsonNode json) {}

    public record PubPrivKeyPEM(String pubKey, String privKey) {}
}

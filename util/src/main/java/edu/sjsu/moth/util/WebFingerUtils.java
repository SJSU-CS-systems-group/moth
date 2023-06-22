package edu.sjsu.moth.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static edu.sjsu.moth.util.WebFingerUtils.MothMimeType.*;

public class WebFingerUtils {

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

        private RelType(String relType) { super(relType); }

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
}

    /**
     * Structure representing the type of a webfinger link
     */
    public record FingerLink(RelType rel, MimeType type, String href) {}

    /**
     * Structure returned by a webfinger request
     */
    public record WebFinger(String subject, List<String> aliases, List<FingerLink> links) {}

    static public WebFinger finger(String user, String host) {
        var temp = new RestTemplate();
        var headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MothMimeType.APPLICATION_ACTIVITY));
        var httpEntity = new HttpEntity<>(headers);
        var uri = MessageFormat.format("https://{1}/.well-known/webfinger?resource=acct:{0}@{1}", user, host);
        var rsp = temp.exchange(uri, HttpMethod.GET, httpEntity, WebFinger.class);
        return rsp.getStatusCode().is2xxSuccessful() ? rsp.getBody() : null;
    }

    public record FingerAndAccount (WebFinger finger, JsonNode json) {}

    static private final Pattern USER_URL_PATTERN = Pattern.compile("https://([^/]+).*/([^/]+)");
    static public FingerAndAccount resolve(String userUrl) {
        WebFinger finger = null;
        JsonNode json = null;
        var temp = new RestTemplate();
        var headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MothMimeType.APPLICATION_ACTIVITY));
        var httpEntity = new HttpEntity<>(headers);
        var rsp = temp.exchange(userUrl, HttpMethod.GET, httpEntity, JsonNode.class);
        json = rsp.getStatusCode().is2xxSuccessful() ? rsp.getBody() : null;

        var match = USER_URL_PATTERN.matcher(userUrl);
        if (match.find()) {
            finger = finger(match.group(2), match.group(1));
        }
        return new FingerAndAccount(finger, json);
    }
}

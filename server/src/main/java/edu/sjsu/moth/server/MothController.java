package edu.sjsu.moth.server;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import static java.text.MessageFormat.format;

@EnableMongoRepositories
@RestController
public class MothController {

    public static final Pattern RESOURCE_PATTERN = Pattern.compile("acct:([^@]+)@(.+)");
    @Autowired
    WebfingerRepository webfingerRepo;

    @GetMapping("/")
    public String index() {
        return "hello";
    }

    @GetMapping("/.alias")
    public String alias(@RequestParam String alias, @RequestParam String user, @RequestParam String host) {
        webfingerRepo.save(new WebfingerAlias(alias, user, host));
        return "added";
    }

    @GetMapping("/.well-known/webfinger")
    public WebFinger webfinger(@RequestParam String resource) {
        var match = (RESOURCE_PATTERN.matcher(resource));
        if (match.find()) {
            var user = match.group(1);
            var foundUser = webfingerRepo.findItemByName(user);
            if (foundUser != null) {
                var host = match.group(2);
                var textLink = format("https://{1}/@{0}", foundUser.user, foundUser.host);
                var activityLink = format("https://{1}/users/{0}", foundUser.user, foundUser.host);
                return new WebFinger(resource, new String[] { textLink, activityLink },
                        new FingerLink[] { new FingerLink(RelType.PROFILE, MimeType.TEXT_HTML, textLink),
                                new FingerLink(RelType.SELF, MimeType.APPLICATION_ACTIVITY, activityLink) });
            }
        }
        return null;
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
    }

    static public class MimeType extends StringType {
        static public final MimeType TEXT_HTML = new MimeType("text/html");
        static public final MimeType APPLICATION_ACTIVITY = new MimeType("application/activity+json");

        private MimeType(String mimeType) {super(mimeType);}
    }

    /**
     * Structure representing the type of a webfinger link
     */
    public record FingerLink(RelType rel, MimeType type, String href) {}

    /**
     * Structure returned by a webfinger request
     */
    public record WebFinger(String subject, String[] aliases, FingerLink[] links) {}

    @RequestMapping("/**")
    public ResponseEntity<String> unexpected(
            HttpServletRequest request
    ) {
        System.out.println(request.getMethod());
        System.out.println(request.getRequestURI());
        var map = request.getParameterMap();
        if (map != null) map.entrySet().forEach(e -> System.out.println(e.getKey() + " = " + Arrays.toString(e.getValue())));
        try {
            var is = request.getInputStream();
            System.out.println(new String(is.readAllBytes()));
        } catch (IOException e) {
        }
        return new ResponseEntity<>("Sorry, not found :'(", HttpStatus.NOT_FOUND);
    }
}

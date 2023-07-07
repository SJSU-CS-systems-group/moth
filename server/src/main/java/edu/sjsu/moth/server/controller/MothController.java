package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.WebFingerUtils;
import edu.sjsu.moth.util.WebFingerUtils.FingerLink;
import edu.sjsu.moth.util.WebFingerUtils.MothMimeType;
import edu.sjsu.moth.util.WebFingerUtils.RelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.text.MessageFormat.format;
import static java.util.Map.entry;

@EnableMongoRepositories
@RestController
public class MothController {
    public static final Pattern RESOURCE_PATTERN = Pattern.compile("acct:([^@]+)@(.+)");
    // andre will set this from the commandline
    public static final String BASE_URL = "https://" + MothConfiguration.mothConfiguration.getServerName();
    public static final String HOSTNAME = MothConfiguration.mothConfiguration.getServerName();

    Logger LOG = Logger.getLogger(MothController.class.getName());
    @Autowired
    WebfingerRepository webfingerRepo;
    @Autowired
    AccountService accountService;

    @GetMapping("/")
    public String index(Principal user) {return "hello sub %s".formatted(user == null ? null : user.getName());}

    @GetMapping("/.well-known/host-meta")
    public ResponseEntity<String> hostMeta() {
        return ResponseEntity.ok("""
                                         <?xml version="1.0" encoding="UTF-8"?>
                                         <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
                                             <Link rel="lrdd" template="https://%s/.well-known/webfinger?resource={uri}"/>
                                         </XRD>
                                         """.formatted(MothConfiguration.mothConfiguration.getServerName()));
    }

    @GetMapping("/.well-known/webfinger")
    public Mono<ResponseEntity<WebFingerUtils.WebFinger>> webfinger(@RequestParam(required = false) String resource) {
        if (resource == null) {
            return Mono.just(ResponseEntity.badRequest().contentType(MothMimeType.APPLICATION_ACTIVITY).build());
        }
        var match = (RESOURCE_PATTERN.matcher(resource));
        if (match.find()) {
            var user = match.group(1);
            return webfingerRepo.findItemByName(user).map(foundUser -> {
                if (foundUser != null) {
                    var host = match.group(2);
                    var textLink = format("https://{1}/@{0}", foundUser.user, foundUser.host);
                    var activityLink = format("https://{1}/users/{0}", foundUser.user, foundUser.host);
                    LOG.fine("finger directing " + user + " to " + activityLink);
                    var links = List.of(new FingerLink(RelType.PROFILE, MimeTypeUtils.TEXT_HTML_VALUE, textLink),
                                        new FingerLink(RelType.SELF, MothMimeType.APPLICATION_ACTIVITY_VALUE,
                                                       activityLink));
                    return ResponseEntity.ok(
                            new WebFingerUtils.WebFinger(resource, List.of(textLink, activityLink), links));
                }
                return null;
            });
        }
        return null;
    }

    // based on https://www.w3.org/TR/activitypub/#actor-objects
    // https://w3id.org/security/v1 came from looking at an example response from a mastodon server
    @GetMapping("/users/{id}")
    Mono<ResponseEntity<Object>> getProfile(@PathVariable String id) {
        return accountService.getAccount(id)
                .flatMap(acct -> accountService.getPublicKey(id, true).map(p -> Pair.of(p, acct)))
                .map(p -> {
                    var pubPem = p.getFirst();
                    var account = p.getSecond();
                    LOG.fine("getting profile for " + id);
                    var profileURL = BASE_URL + "/users/" + id;
                    var pem = new WebFingerUtils.PublicKeyMessage(profileURL, pubPem);
                    var endpoints = new WebFingerUtils.ProfileEndpoints(BASE_URL + "/inbox");
                    var profile = Map.ofEntries(entry("@context", List.of("https://www.w3.org/ns/activitystreams",
                                                                          "https://w3id.org/security/v1")),
                                                entry("type", "Person"), entry("id", profileURL),
                                                entry("following", profileURL + "/following"),
                                                entry("followers", profileURL + "/followers"),
                                                entry("inbox", profileURL + "/inbox"),
                                                entry("outbox", profileURL + "/outbox"),
                                                entry("featured", profileURL + "/collections/featured"),
                                                entry("featuredTags", profileURL + "/collections/tags"),
                                                entry("preferredUsername", id), entry("name", account.username),
                                                entry("summary", account.note), entry("url", BASE_URL + "/@" + id),
                                                entry("published", account.created_at), entry("publicKey", pem),
                                                entry("endpoints", endpoints));
                    return ResponseEntity.ok(profile);
                });
    }
}

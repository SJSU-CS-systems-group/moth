package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import edu.sjsu.moth.generated.Icon;
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
import java.util.ArrayList;
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
                .flatMap(acct -> accountService.getPublicKey(id, true).map(p -> Pair.of(p, acct))).map(p -> {
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

    @GetMapping("/manifest.json")
    public Mono<ManifestJSON> manifest() {
        String name = MothConfiguration.mothConfiguration.getServerName();
        String short_name = MothConfiguration.mothConfiguration.getServerName();

        List<Icon> icons = new ArrayList<>();
        Icon x32 = new Icon("image/png", null, null, "moth/icons/cyber-moth-32.png", "32x32", "any maskable");
        Icon x48 = new Icon("image/png", null, null, "moth/icons/cyber-moth-48.png", "48x48", "any maskable");
        Icon x144 = new Icon("image/png", null, null, "moth/icons/cyber-moth-144.png", "144x144", "any maskable");
        Icon x256 = new Icon("image/png", null, null, "moth/icons/cyber-moth-256.png", "256x256", "any maskable");
        Icon x512 = new Icon("image/png", null, null, "moth/icons/cyber-moth-512x512.png", "512x512", "any maskable");
        icons.add(x32);
        icons.add(x48);
        icons.add(x144);
        icons.add(x256);
        icons.add(x512);

        String theme_color = "#FFFFFF";
        String background_color = "#FFFFFF";
        String display = "standalone";
        String start_url = "/home";
        String scope = "/";

        // unsure
        ShareTarget share_target = new ShareTarget("share?title={title}&text={text}&url={url}", "share", "GET",
                                                   "application/x-www-form-urlencoded",
                                                   new Params("title", "text", "url"));

        List<Shortcut> shortcuts = new ArrayList<>(); //may need to change to null
        shortcuts.add(new Shortcut("name", "url"));

        return Mono.just(
                new ManifestJSON(name, short_name, icons, theme_color, background_color, display, start_url, scope,
                                 share_target, shortcuts));
    }

    // TODO: add data in Usage
    // TODO: to grab from account DB
    @GetMapping("/nodeinfo/2.0")
    public Mono<NodeInfo2> nodeInfo2Mono() {
        return Mono.just(new NodeInfo2("2.0", new Software("mastodon", "4.2.8"), List.of("activitypub"),
                                       new Services(List.of(""), List.of("")), new Usage(new Users(0, 0, 0), 0), true,
                                       new Metadata()));
    }

    @GetMapping("/.well-known/nodeinfo")
    public Mono<NodeInfo> nodeInfoMono() {
        return Mono.just(new NodeInfo(List.of(new Link("http://nodeinfo.diaspora.software/ns/schema/2.0", "https://" +
                MothConfiguration.mothConfiguration.getServerName() + "/nodeinfo/2.0"))));
        // added placeholders, hardcoded
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "name", "short_name", "icons", "theme_color", "background_color", "display", "start_url",
            "scope", "share_target", "shortcuts" })
    public record ManifestJSON(String name, String short_name, List<Icon> icons, String theme_color,
                               String background_color, String display, String start_url, String scope,
                               ShareTarget share_target, List<Shortcut> shortcuts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "links" })
    public record NodeInfo(List<Link> links) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "version", "software", "protocols", "services", "usage", "openRegistrations", "metadata" })
    public record NodeInfo2(String version, Software software, List<String> protocols, Services services, Usage usage,
                            boolean openRegistrations, Metadata metadata) {}

    public record Software(String name, String version) {}

    // Unsure if it is List of Strings.
    public record Services(List<String> outbound, List<String> inbound) {}

    public record Usage(Users user, int localPosts) {}

    public record Users(int total, int activeMonth, int activeHalfyear) {}

    public record Metadata() {}

    public record Shortcut(String name, String url) {}

    public record Params(String title, String text, String url) {}

    public record ShareTarget(String url_template, String action, String method, String enctype, Params params) {}

    public record Link(String rel, String href) {}
}

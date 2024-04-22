package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.generated.Attachment;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.generated.Icon;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.ActorService;
import edu.sjsu.moth.server.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static edu.sjsu.moth.server.util.Util.printJsonNode;
import edu.sjsu.moth.server.util.MothConfiguration;

@RestController
public class InboxController {
    @Autowired
    StatusService statusService;

    @Autowired
    ActorService actorService;

    @Autowired
    AccountService accountService;

    //required to map payload from JSON to a Java Object for data access
    ObjectMapper mappedLoad;

    public InboxController(ObjectMapper mappedLoad) {
        this.mappedLoad = mappedLoad;
    }

    public static Mono<Account> convertToAccount(Actor actor) {
        String serverName = "";
        try {
            serverName = new URL(actor.id).getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        ArrayList<AccountField> accountFields = new ArrayList<>();
        for (Attachment attachment : actor.attachment) {
            AccountField accountField = new AccountField(attachment.name, attachment.value, null);
            accountFields.add(accountField);
        }

        String iconLink;
        if (actor.icon != null) {
            iconLink = actor.icon.url;
        } else {
            iconLink = "";
        }

        String imageLink;
        if (actor.image != null) {
            imageLink = actor.image.url;
        } else {
            imageLink = "";
        }

        WebClient webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/activity+json")
                .build();
        Mono<JsonNode> outboxResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followersResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followingResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        String finalServerName = serverName;
        return outboxResponse.flatMap(jsonNodeOutbox -> {
            int totalItems = jsonNodeOutbox.get("totalItems").asInt();
            return followersResponse.flatMap(jsonNodeFollowers -> {
                int totalItemFollowers = jsonNodeFollowers.get("totalItems").asInt();
                return followingResponse.map(jsonNodeFollowing -> {
                    int totalItemFollowing = jsonNodeFollowing.get("totalItems").asInt();
                    //change avatar, avatar static, header, header static, last status to "" from iconLink and imageLink
                    //change from String.valueOf(generateUniqueId()) to just their name
                    //changed last status from null to actor.published
                    return new Account(actor.preferredUsername, actor.preferredUsername,
                                       actor.preferredUsername + "@" + finalServerName, actor.url, actor.name,
                                       actor.summary, iconLink, iconLink, imageLink, imageLink,
                                       actor.manuallyApprovesFollowers, accountFields, new CustomEmoji[0], false, false,
                                       actor.discoverable, false, false, false, false, actor.published, actor.published,
                                       totalItems, totalItemFollowers, totalItemFollowing);
                });
            });
        });
        //don't know about custom emojis, bot, and group
        //noindex, moved, suspended, and limited are optional?
        //icon, image, tag, attachment might be null
        //not sure how to get last_status_at. outbox doesn't give a time, only the last status
    }

    @PostMapping("/inbox")
    public Mono<ResponseEntity<Object>> inbox(@RequestBody JsonNode inboxNode) {
        //handle here
        printJsonNode(inboxNode, " ");
        String requestType = inboxNode.get("type").asText();
        if (requestType.equals("Delete")) {
            return Mono.empty();
        } else if (requestType.equals("Create")) {
            return createHandler(inboxNode);
        } else if (requestType.equals("Update")) {
            System.out.println("I've seen UPDATE and it is going to be supported soon");
            return Mono.empty();
        } else {
            return Mono.error(new RuntimeException(requestType + " is not supported yet because I've never seen it"));
        }
    }

    public Mono<ResponseEntity<Object>> createHandler(@RequestBody JsonNode node) {
        JsonNode objNode = node.get("object");
        String toLink = objNode.get("id").asText();

        //putting in variables for now to make it easier to read
        String id = toLink.substring(toLink.indexOf("/statuses/") + "/statuses/".length());
        String createdAt = node.get("published").asText();
        String inReplyTo = objNode.get("inReplyTo").asText();
        Boolean sensitive = objNode.get("sensitive").asText().equals("true");
        String language = objNode.get("contentMap").fields().next().getKey();
        String content = objNode.get("content").asText();

        //Making an actor and then converting to account
        String accountLink = node.get("actor").asText();
        return actorService.getActor(accountLink)
                .switchIfEmpty(createActor(accountLink))
                .flatMap(actor -> convertToAccount(actor))
                .flatMap(account -> {
                    //not sure about spoiler text
                    //haven't implemented media service yet, not sure about visibility
                    //changed inreplyto to null
                    ExternalStatus status = new ExternalStatus(null, createdAt, null, null, sensitive, "", "direct",
                                                               language, null, null, 0, 0, 0, false, false, false,
                                                               false, content, null, null, account, List.of(),
                                                               List.of(), List.of(), List.of(), null, null, content,
                                                               node.get("published").asText());
                    return statusService.saveExternal(status).map(ResponseEntity::ok);
                });
    }

    public Mono<Actor> createActor(String accountLink) {
        WebClient webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/activity+json")
                .build();
        Mono<Actor> response = webClient.get().uri(accountLink).retrieve().bodyToMono(Actor.class);
        return response.flatMap(actor -> actorService.save(actor));
    }

    @PostMapping("/users/{id}/inbox")
    public Mono<String> usersInbox(@PathVariable String id, @RequestBody JsonNode inboxNode) {
        String requestType = inboxNode.get("type").asText();
        // follow or unfollow requests
        if (requestType.equals("Follow") || requestType.equals("Undo"))
            return accountService.followerHandler(id, inboxNode, requestType);
        return Mono.empty();
    }

    @GetMapping("/users/{id}/following")
    public Mono<UsersFollowResponse> usersFollowing(@PathVariable String id,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer limit) {
        return accountService.usersFollow(id, page, limit, "following");
    }

    @GetMapping("/users/{id}/followers")
    public Mono<UsersFollowResponse> usersFollowers(@PathVariable String id,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer limit) {
        return accountService.usersFollow(id, page, limit, "followers");
    }

    @GetMapping("/manifest.json")
    public Mono<ManifestJSON> manifest() {
        String name = MothConfiguration.mothConfiguration.getServerName();
        String short_name = MothConfiguration.mothConfiguration.getServerName();

        List<Icon> icons = new ArrayList<>();
        Icon x32 = new Icon("image/png", null, null, "moth/icons/cyber-moth-32.png",
                            "32x32", "any maskable");
        Icon x48 = new Icon("image/png", null, null, "moth/icons/cyber-moth-48.png",
                            "48x48", "any maskable");
        Icon x144 = new Icon("image/png", null, null, "moth/icons/cyber-moth-144.png",
                             "144x144", "any maskable");
        Icon x256 = new Icon("image/png", null, null, "moth/icons/cyber-moth-256.png",
                             "256x256", "any maskable");
        Icon x512 = new Icon("image/png", null, null, "moth/icons/cyber-moth-512x512.png",
                             "512x512", "any maskable");
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

    //TODO: add data in Usage
    @GetMapping("/nodeinfo/2.0")
    public Mono<NodeInfo2> nodeInfo2Mono() {
        return Mono.just(new NodeInfo2("2.0", new Software("mastodon", "4.2.8"), List.of("activitypub"), new Services(List.of(""), List.of("")),
                                       new Usage(new Users(0, 0, 0), 0), true, new Metadata()));
    }

    @GetMapping("/.well-known/nodeinfo")
    public Mono<NodeInfo> nodeInfoMono() {
        return Mono.just(new NodeInfo(
                List.of(new Link("http://nodeinfo.diaspora.software/ns/schema/2.0", "https://mas.to/nodeinfo/2.0"))));
        // added placeholders, hardcoded
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "@context", "id", "type", "totalItems", "first", "next", "partOf", "orderedItems" })
    public record UsersFollowResponse(String id, String type, int totalItems, String first, String next, String partOf,
                                      List<String> orderedItems) {
        @JsonProperty("@context")
        public String getContext() {
            return "https://www.w3.org/ns/activitystreams";
        }
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

    public record Software(String name, String version){};
    // Unsure if it is List of Strings.
    public record Services(List<String> outbound, List<String> inbound){};
    public record Usage(Users user, int localPosts){};
    public record Users(int total, int activeMonth, int activeHalfyear){};
    public record Metadata(){};
    public record Shortcut(String name, String url){};
    public record Params(String title, String text, String url){};
    public record ShareTarget(String url_template, String action, String method, String enctype, Params params){};
    public record Link(String rel, String href){};
}
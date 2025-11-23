package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.generated.Attachment;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.ActorService;
import edu.sjsu.moth.server.service.StatusService;
import edu.sjsu.moth.server.util.MothConfiguration;
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
import java.util.Collections;
import java.util.List;

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

        WebClient webClient =
                WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
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

    // mapping media to img
    private static String mapMediaTypeToMastoType(String mediaType, String asType) {
        if (mediaType == null) mediaType = "";
        String lower = mediaType.toLowerCase();
        if (lower.startsWith("image/")) return "image";
        if (lower.equals("image/gif")) return "gifv";
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("audio/")) return "audio";
        if ("Image".equalsIgnoreCase(asType)) return "image";
        if ("Video".equalsIgnoreCase(asType)) return "video";
        if ("Audio".equalsIgnoreCase(asType)) return "audio";
        return "image";
    }

    // get the media url
    private static String coalesceUrlFromAttachment(JsonNode a) {
        if (a.has("url") && a.get("url").isTextual()) return a.get("url").asText();
        if (a.has("href") && a.get("href").isTextual()) return a.get("href").asText();
        if (a.has("url") && a.get("url").isArray()) {
            for (JsonNode u : a.get("url")) {
                if (u.has("href") && u.get("href").isTextual()) return u.get("href").asText();
                if (u.isTextual()) return u.asText();
            }
        }
        return null;
    }

    // preview url or thumbnail url
    private static String coalescePreviewFromAttachment(JsonNode a) {
        if (a.has("icon") && a.get("icon").has("url") && a.get("icon").get("url").isTextual()) {
            return a.get("icon").get("url").asText();
        }
        if (a.has("url") && a.get("url").isArray()) {
            for (JsonNode u : a.get("url")) {
                String mediaType =
                        u.has("mediaType") && u.get("mediaType").isTextual() ? u.get("mediaType").asText() : "";
                String href = u.has("href") && u.get("href").isTextual() ? u.get("href").asText() : null;
                if (mediaType.startsWith("image/") && href != null) return href;
            }
        }
        return null;
    }

    @PostMapping("/inbox")
    public Mono<ResponseEntity<Object>> inbox(@RequestBody JsonNode inboxNode) {
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
        return actorService.getActor(accountLink).switchIfEmpty(createActor(accountLink))
                .flatMap(actor -> convertToAccount(actor)).flatMap(account -> {
                    // build media attachments
                    var media = new java.util.ArrayList<edu.sjsu.moth.generated.MediaAttachment>();
                    var attachments = objNode.get("attachment");
                    if (attachments != null && attachments.isArray()) {
                        int idx = 0;
                        for (JsonNode a : attachments) {
                            String attachId = a.has("id") && a.get("id").isTextual() ? a.get("id").asText() :
                                    (id + ":att:" + idx);
                            String mediaType =
                                    a.has("mediaType") && a.get("mediaType").isTextual() ? a.get("mediaType").asText() :
                                            "";
                            String asType = a.has("type") && a.get("type").isTextual() ? a.get("type").asText() : null;
                            String type = mapMediaTypeToMastoType(mediaType, asType);

                            String url = coalesceUrlFromAttachment(a);
                            String previewUrl = coalescePreviewFromAttachment(a);
                            if (previewUrl == null) previewUrl = url;

                            String description = a.has("name") && a.get("name").isTextual() ? a.get("name").asText() :
                                    (a.has("summary") && a.get("summary").isTextual() ? a.get("summary").asText() :
                                            null);

                            java.util.Map<String, Object> meta = java.util.Map.of();
                            if (a.has("width") && a.has("height") && a.get("width").canConvertToInt() &&
                                    a.get("height").canConvertToInt()) {
                                int width = a.get("width").asInt();
                                int height = a.get("height").asInt();
                                double aspect = height == 0 ? 0.0 : (double) width / (double) height;
                                java.util.Map<String, Object> original = new java.util.HashMap<>();
                                original.put("width", width);
                                original.put("height", height);
                                original.put("size", width + "x" + height);
                                original.put("aspect", aspect);
                                java.util.Map<String, Object> m = new java.util.HashMap<>();
                                m.put("original", original);
                                meta = m;
                            }

                            String blurhash =
                                    a.has("blurhash") && a.get("blurhash").isTextual() ? a.get("blurhash").asText() :
                                            null;

                            media.add(new edu.sjsu.moth.generated.MediaAttachment(attachId, type, url, previewUrl, url,
                                                                                  null, meta, description, blurhash));
                            idx++;
                        }
                    }

                    // saving it to ExternalStatuses db
                    ExternalStatus status =
                            new ExternalStatus(null, createdAt, null, null, sensitive, "", "direct", language, null,
                                               null, 0, 0, 0, false, false, false, false, content, null, null, account,
                                               media, List.of(), List.of(), List.of(), null, null, content,
                                               node.get("published").asText());
                    return statusService.saveExternal(status).map(ResponseEntity::ok);
                });
    }

    public Mono<Actor> createActor(String accountLink) {
        WebClient webClient =
                WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        Mono<Actor> response = webClient.get().uri(accountLink).retrieve().bodyToMono(Actor.class);
        return response.flatMap(actor -> actorService.save(actor));
    }

    @PostMapping("/users/{id}/inbox")
    public Mono<String> usersInbox(@PathVariable String id, @RequestBody JsonNode inboxNode) {
        String requestType = inboxNode.get("type").asText();
        // follow or unfollow requests from other or same instances
        switch (requestType) {
            case "Follow" -> {
                return accountService.followerHandler(id, inboxNode, false);
            }
            case "Undo" -> {
                return accountService.followerHandler(id, inboxNode, true);
            }
            case "Accept" -> {
                return accountService.acceptHandler(id, inboxNode);
            }
            default -> {
                return Mono.empty();
            }
        }
    }

    @GetMapping("/users/{id}/following")
    public Mono<UsersFollowResponse> usersFollowing(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return accountService.usersFollow(id, page, limit, "following");
    }

    @GetMapping("/users/{id}/followers")
    public Mono<UsersFollowResponse> usersFollowers(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return accountService.usersFollow(id, page, limit, "followers");
    }

    @GetMapping("/users/{id}/collections/featured")
    public Mono<ResponseEntity<OrderedCollection>> getFeaturedCollection(@PathVariable String id) {
        String collectionId =
                MothConfiguration.mothConfiguration.getServerName() + "/users/" + id + "/collections/featured";
        OrderedCollection featuredCollection = new OrderedCollection(collectionId, 0, Collections.emptyList());

        return Mono.just(ResponseEntity.ok(featuredCollection));
    }

    @GetMapping("/users/{id}/collections/tags")
    public Mono<ResponseEntity<OrderedCollection>> getTagsCollection(@PathVariable String id) {
        String collectionId =
                MothConfiguration.mothConfiguration.getServerName() + "/users/" + id + "/collections/tags";
        OrderedCollection tagsCollection = new OrderedCollection(collectionId, 0, Collections.emptyList());

        return Mono.just(ResponseEntity.ok(tagsCollection));
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
    @JsonPropertyOrder({ "@context", "id", "type", "totalItems", "orderedItems" })
    public record OrderedCollection(@JsonProperty("@context") String context, String id, String type, int totalItems,
                                    List<Object> orderedItems) {
        public OrderedCollection(String id, int totalItems, List<Object> orderedItems) {
            this("https://www.w3.org/ns/activitystreams", id, "OrderedCollection", totalItems, orderedItems);
        }
    }
}

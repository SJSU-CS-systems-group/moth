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
import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.Following;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.ActorService;
import edu.sjsu.moth.server.service.FollowerService;
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
import java.util.Collections;
import java.util.List;

import static edu.sjsu.moth.server.util.Util.generateUniqueId;
import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@RestController
public class InboxController {
    @Autowired
    StatusService statusService;

    @Autowired
    ActorService actorService;

    @Autowired
    AccountService accountService;

    @Autowired
    FollowerService followerService;

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
            iconLink = null;
        }

        String imageLink;
        if (actor.image != null) {
            imageLink = actor.image.url;
        } else {
            imageLink = null;
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
                    return new Account(String.valueOf(generateUniqueId()), actor.preferredUsername,
                                       actor.preferredUsername + "@" + finalServerName, actor.url, actor.name,
                                       actor.summary, iconLink, iconLink, imageLink, imageLink,
                                       actor.manuallyApprovesFollowers, accountFields, new CustomEmoji[0], false, false,
                                       actor.discoverable, false, false, false, false, actor.published, null,
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
                    ExternalStatus status = new ExternalStatus(id, createdAt, inReplyTo, inReplyTo, sensitive, "",
                                                               "direct", language, null, null, 0, 0, 0, false, false,
                                                               false, false, content, null, null, account, List.of(),
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
        return response.flatMap(actor -> {
            return actorService.save(actor);
        });
    }

    @PostMapping("/users/{id}/inbox")
    public Mono<String> usersInbox(@PathVariable String id, @RequestBody JsonNode inboxNode) {
        String requestType = inboxNode.get("type").asText();
        // follow or unfollow requests
        if (requestType.equals("Follow") || requestType.equals("Undo"))
            return followerHandler(id, inboxNode, requestType);
        return Mono.empty();
    }

    public Mono<String> followerHandler(String id, JsonNode inboxNode, String requestType) {
        String follower = inboxNode.get("actor").asText();
        if (requestType.equals("Follow")) {
            // check id
            if (accountService.getAccount(id) == null) {
                return Mono.error(new RuntimeException("Error: Account to follow doesn't exist."));
            }
            // find id, grab arraylist, append
            else {
                return followerService.getFollowersById(id)
                        .switchIfEmpty(Mono.just(new Followers(id, new ArrayList<>())))
                        .flatMap(followedUser -> {
                            followedUser.getFollowers().add(follower);
                            return followerService.saveFollowers(followedUser).thenReturn("done");
                        });
            }
        }
        if (requestType.equals("Undo")) {
            // find id, grab arraylist, remove
            return followerService.getFollowersById(id).flatMap(followedUser -> {
                followedUser.getFollowers().remove(follower);
                return followerService.saveFollowers(followedUser).thenReturn("done");
            });
        }
        return Mono.empty();
    }

    @GetMapping("/users/{id}/following")
    public Mono<UsersFollowResponse> usersFollowing(@PathVariable String id,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer limit) {
        return usersFollow(id, page, limit, "following");
    }

    @GetMapping("/users/{id}/followers")
    public Mono<UsersFollowResponse> usersFollowers(@PathVariable String id,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer limit) {
        return usersFollow(id, page, limit, "followers");
    }

    public Mono<UsersFollowResponse> usersFollow(String id, @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer limit, String followType) {
        var items = followType.equals("following") ? followerService.getFollowingById(id)
                .map(Following::getFollowing) : followerService.getFollowersById(id).map(Followers::getFollowers);
        String returnID = MothController.BASE_URL + "/users/" + id + followType;
        int pageSize = limit != null ? limit : DEFAULT_PAGE_SIZE;
        if (page == null) {
            String first = returnID + "?page=1";
            return items.map(
                    v -> new UsersFollowResponse(returnID, "OrderedCollection", v.size(), first, null, null, null));
        } else { // page number is given
            int pageNum = page < 1 ? 1 : page;
            return items.map(v -> {
                String newReturnID = limit != null ? returnID + "?page=" + page + "&limit=" + limit : returnID +
                        "?page=" + page;
                if (pageNum * pageSize >= v.size()) { // no next page
                    return new UsersFollowResponse(newReturnID, "OrderedCollectionPage", v.size(), null, null, returnID,
                                                   paginateFollowers(v, pageNum, pageSize));
                } else {
                    String next = returnID + "?page=" + (pageNum + 1);
                    if (limit != null) {
                        next += "&limit=" + limit;
                    }
                    return new UsersFollowResponse(newReturnID, "OrderedCollectionPage", v.size(), null, next, returnID,
                                                   paginateFollowers(v, pageNum, pageSize));
                }
            });
        }
    }

    public List<String> paginateFollowers(ArrayList<String> followers, int pageNo, int pageSize) {
        int startIndex = (pageNo - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, followers.size());
        if (startIndex >= followers.size()) {
            return Collections.emptyList();
        }
        return followers.subList(startIndex, endIndex);
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
}
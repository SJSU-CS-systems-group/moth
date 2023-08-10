package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Actor;
import edu.sjsu.moth.server.db.ConversationsRepository;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.FollowersRepository;
import edu.sjsu.moth.server.db.Following;
import edu.sjsu.moth.server.db.FollowingRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.MediaService;
import edu.sjsu.moth.server.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@RestController
public class InboxController {
    @Autowired
    StatusService statusService;

    @Autowired
    AccountService accountService;

    @Autowired
    MediaService mediaService;

    @Autowired
    FollowersRepository followersRepository;

    @Autowired
    FollowingRepository followingRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    ExternalStatusRepository externalStatusRepository;

    @Autowired
    ExternalActorRepository externalActorRepository;

    @Autowired
    ConversationsRepository conversationsRepository;

    //required to map payload from JSON to a Java Object for data access
    ObjectMapper mappedLoad;

    public InboxController(ObjectMapper mappedLoad) {
        this.mappedLoad = mappedLoad;
    }

    //Print method, testing purposes
    public static void printJsonNode(JsonNode node, String indent) {
        if (node.isObject()) {
            // Print keys and their values for object nodes
            node.fields().forEachRemaining(entry -> {
                System.out.println(indent + entry.getKey() + ": ");
                printJsonNode(entry.getValue(), indent + "    "); // Increase indentation for nested objects
            });
        } else if (node.isArray()) {
            // Print each element in the array
            for (JsonNode element : node) {
                printJsonNode(element, indent);
            }
        } else if (node.isValueNode()) {
            // Print the value of scalar nodes
            String value = node.asText();
            if (value.isEmpty()) {
                System.out.println();
            } else {
                System.out.println(indent + value);
            }
        }
    }

    @PostMapping("/inbox")
    public Mono<String> inbox(@RequestBody JsonNode inboxNode) {
        //print out what it gives
        System.out.println("\nSTART####################################################################\n");
        printJsonNode(inboxNode, " ");
        System.out.println("\nEND######################################################################\n");

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

    public Mono<String> createHandler(@RequestBody JsonNode node) {
        JsonNode objNode = node.get("object");
        String toLink = objNode.get("id").asText();

        //putting in variables for now to make it easier to read
        String accountName = node.get("actor")
                .asText()
                .substring(node.get("actor").asText().indexOf("/users/") + "statuses/".length());
        String id = toLink.substring(toLink.indexOf("/statuses/") + "/statuses/".length());
        String createdAt = node.get("published").asText();
        String inReplyTo = objNode.get("inReplyTo").asText();
        Boolean sensitive = objNode.get("sensitive").asText().equals("true");
        String language = objNode.get("contentMap").fields().next().getKey();
        String content = objNode.get("content").asText();

        //Making an actor and then converting to account
        String accountLink = node.get("actor").asText();
        if (externalActorRepository.findItemById(accountLink) != null) {
            externalActorRepository.findItemById(accountLink).flatMap(Actor::convertToAccount).subscribe(account -> {
                //not sure about spoiler text
                //havent implemented media service yet, not sure about visibility
                Status status = new Status(id, createdAt, inReplyTo, inReplyTo, sensitive, "", "direct", language, null,
                                           null, 0, 0, 0, false, false, false, false, content, null, null, account,
                                           List.of(), List.of(), List.of(), List.of(), null, null, content,
                                           node.get("published").asText());
                externalStatusRepository.save(status);
            });
        } else {
            WebClient webClient = WebClient.builder()
                    .defaultHeader(HttpHeaders.ACCEPT, "application/activity+json")
                    .build();
            Mono<Actor> response = webClient.get().uri(accountLink).retrieve().bodyToMono(Actor.class);
            response.flatMap(actor -> {
                externalActorRepository.save(actor);
                return actor.convertToAccount();
            }).subscribe(account -> {
                //not sure about spoiler text
                //havent implemented media service yet, not sure about visibility
                Status status = new Status(id, createdAt, inReplyTo, inReplyTo, sensitive, "", "direct", language, null,
                                           null, 0, 0, 0, false, false, false, false, content, null, null, account,
                                           List.of(), List.of(), List.of(), List.of(), null, null, content,
                                           node.get("published").asText());
                externalStatusRepository.save(status);
            });
        }
        return Mono.empty();
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
            if (accountRepository.findItemByAcct(id) == null) {
                return Mono.error(new RuntimeException("Error: Account to follow doesn't exist."));
            }
            // find id, grab arraylist, append
            else {
                return followersRepository.findItemById(id)
                        .switchIfEmpty(Mono.just(new Followers(id, new ArrayList<>())))
                        .flatMap(followedUser -> {
                            followedUser.getFollowers().add(follower);
                            return followersRepository.save(followedUser).thenReturn("done");
                        });
            }
        }
        if (requestType.equals("Undo")) {
            // find id, grab arraylist, remove
            return followersRepository.findItemById(id).flatMap(followedUser -> {
                followedUser.getFollowers().remove(follower);
                return followersRepository.save(followedUser).thenReturn("done");
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
        var items = followType.equals("following") ? followingRepository.findItemById(id)
                .map(Following::getFollowing) : followersRepository.findItemById(id).map(Followers::getFollowers);
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
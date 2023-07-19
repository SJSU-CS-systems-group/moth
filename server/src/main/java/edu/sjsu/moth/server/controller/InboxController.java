package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.FollowersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@RestController
public class InboxController {
    @Autowired
    FollowersRepository followersRepository;


    //required to map payload from JSON to a Java Object for data access
    ObjectMapper mappedLoad;

    public InboxController(ObjectMapper mappedLoad) {
        this.mappedLoad = mappedLoad;
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
            // find id, grab arraylist, append
            return followersRepository.findItemById(id).flatMap(followedUser -> {
                followedUser.getFollowers().add(follower);
                return followersRepository.save(followedUser).thenReturn("done");
            });
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

    @GetMapping("/users/{id}/followers")
    public Mono<UsersFollowersResponse> usersFollowers(@PathVariable String id, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit) {
        var items = followersRepository.findItemById(id).map(Followers::getFollowers);
        String returnID = MothController.BASE_URL + "/users/" + id + "/followers";
        int pageSize = limit != null ? limit : DEFAULT_PAGE_SIZE;
        if(page == null) {
            String first = returnID + "?page=1";
            return items.map(v -> new UsersFollowersResponse(returnID, "OrderedCollection", v.size(), first, null, null, null));
        } else { // page number is given
            int pageNum = page < 1 ? 1 : page;
            return items.map(v -> {
                String newReturnID = limit != null ? returnID + "?page=" + page + "&limit=" + limit : returnID + "?page=" + page;
                if(pageNum*pageSize >= v.size()) { // no next page
                    return new UsersFollowersResponse(newReturnID, "OrderedCollectionPage", v.size(), null, null, returnID, paginateFollowers(v, pageNum, pageSize));
                } else {
                    String next = returnID + "?page=" + (pageNum+1);
                    if(limit != null) {
                        next += "&limit=" + limit;
                    }
                    return new UsersFollowersResponse(newReturnID, "OrderedCollectionPage", v.size(), null, next, returnID, paginateFollowers(v, pageNum, pageSize));
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

    // if there is a next follower page
    @JsonPropertyOrder({"@context", "id", "type", "totalItems", "first", "next", "partOf", "orderedItems"})
    public record UsersFollowersResponse(String id, String type, int totalItems, @JsonInclude(JsonInclude.Include.NON_NULL) String first, @JsonInclude(JsonInclude.Include.NON_NULL) String next, @JsonInclude(JsonInclude.Include.NON_NULL) String partOf, @JsonInclude(JsonInclude.Include.NON_NULL) List<String> orderedItems) {
        @JsonProperty("@context")
        public String getContext() {
            return "https://www.w3.org/ns/activitystreams";
        }
    }



}

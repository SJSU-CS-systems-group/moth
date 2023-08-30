package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.server.db.AccountRepository;

import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.FollowersRepository;
import edu.sjsu.moth.server.db.Following;
import edu.sjsu.moth.server.db.FollowingRepository;
import edu.sjsu.moth.server.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

import java.util.ArrayList;

import java.util.Collections;
import java.util.List;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@RestController
public class InboxController {

    @Autowired
    AccountService accountService;

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

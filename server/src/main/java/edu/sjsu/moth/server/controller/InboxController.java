package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.server.db.FollowersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URL;
import java.util.Collections;

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
    public Mono<String> usersInbox(@PathVariable String id, @RequestBody String payload) {
        try {
            // read JSON to Java Object, then look at request Type
            JsonNode inboxNode = mappedLoad.readTree(payload);
            String requestType = inboxNode.get("type").asText();
            // follow or unfollow requests
            if (requestType.equals("Follow") || requestType.equals("Undo")) {
                return followerHandler(id, inboxNode, requestType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Mono.error(e);
        }
        return Mono.empty();
    }

    public Mono<String> followerHandler(String id, JsonNode inboxNode, String requestType) {
        try {
            String follower = inboxNode.get("actor").asText();
            if (requestType.equals("Follow")) {
                // find id, grab arraylist, append
                return followersRepository.findItemById(id).flatMap(followedUser -> {
                    followedUser.getFollowers().add(follower);
                    return followersRepository.save(followedUser).thenReturn("done");});
            }
            if (requestType.equals("Undo")) {
                // find id, grab arraylist, remove
                return followersRepository.findItemById(id).flatMap(followedUser -> {
                    followedUser.getFollowers().remove(follower);;
                    return followersRepository.save(followedUser).thenReturn("done");});
            }
            return Mono.empty();
        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(e);
        }
    }
}

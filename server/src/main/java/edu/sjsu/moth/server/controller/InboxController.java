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
            // read JSON to Java Object, then construct the ID
            JsonNode inboxNode = mappedLoad.readTree(payload);
            String payloadActor = inboxNode.get("actor").asText();
            URL newFollowerURL = new URL(payloadActor);
            StringBuilder followerUser = new StringBuilder();
            // truncate the last slash, then find the position of the next-to-last one, then +1 to index to grab
            // substring
            String truncatedURL = newFollowerURL.getPath().replaceAll("/$", "");
            int lastSlash = truncatedURL.lastIndexOf('/');
            followerUser.append(truncatedURL.substring(lastSlash + 1) + "@" + newFollowerURL.getHost());
            //if "type: Follow", then construct and append follower to collection. if "type = Undo", remove from doc.
            if (inboxNode.get("type").asText().equals("Follow")) {
                return addFollower(id, followerUser.toString());
            }
            if (inboxNode.get("type").asText().equals("Undo")) {
                return removeFollower(id, followerUser.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(e);
        }
        return Mono.empty();
    }

    public Mono<String> addFollower(String id, String newFollower) {
        return followersRepository.findItemById(id).flatMap(followedUser -> {
            ArrayList<String> userFollowers = followedUser.getFollowers();
            userFollowers.add(newFollower);
            return followersRepository.save(followedUser).thenReturn("done");
        });
    }

    public Mono<String> removeFollower(String id, String followerToRemove) {
        return followersRepository.findItemById(id).flatMap(followedUser -> {
            ArrayList<String> userFollowers = followedUser.getFollowers();
            int index = Collections.binarySearch(userFollowers, followerToRemove);
            userFollowers.remove(index);
            return followersRepository.save(followedUser).thenReturn("done");
        });
    }
}

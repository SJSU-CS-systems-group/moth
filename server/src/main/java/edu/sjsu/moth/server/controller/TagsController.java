package edu.sjsu.moth.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;

@RestController
public class TagsController {

    public record Tag(String name, String url, List<History> history, boolean following) {}

    public record History(String day, String uses, String accounts) {}

    public record FeaturedTag(String id, String name, String url, int statuses_count, String last_status_at) {}

    @GetMapping("/api/v1/followed_tags")
    public Mono<ResponseEntity<List<Tag>>> getFollowedTags(Principal user) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // Followed tags not yet implemented - return empty list
        return Mono.just(ResponseEntity.ok(List.of()));
    }

    @GetMapping("/api/v1/featured_tags")
    public Mono<ResponseEntity<List<FeaturedTag>>> getFeaturedTags(Principal user) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // Featured tags not yet implemented - return empty list
        return Mono.just(ResponseEntity.ok(List.of()));
    }

    @GetMapping("/api/v1/tags/{id}")
    public Mono<ResponseEntity<Tag>> getTag(@PathVariable String id) {
        // Return a basic tag object
        return Mono.just(ResponseEntity.ok(new Tag(id, MothController.BASE_URL + "/tags/" + id, List.of(), false)));
    }

    @PostMapping("/api/v1/tags/{id}/follow")
    public Mono<ResponseEntity<Tag>> followTag(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // Tag following not yet implemented - return the tag as if followed
        return Mono.just(ResponseEntity.ok(new Tag(id, MothController.BASE_URL + "/tags/" + id, List.of(), true)));
    }

    @PostMapping("/api/v1/tags/{id}/unfollow")
    public Mono<ResponseEntity<Tag>> unfollowTag(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // Tag unfollowing not yet implemented - return the tag as if unfollowed
        return Mono.just(ResponseEntity.ok(new Tag(id, MothController.BASE_URL + "/tags/" + id, List.of(), false)));
    }

    @PostMapping("/api/v1/featured_tags")
    public Mono<ResponseEntity<FeaturedTag>> featureTag(Principal user) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // Featured tags not yet implemented - return empty response
        return Mono.just(ResponseEntity.ok(new FeaturedTag("0", "", "", 0, null)));
    }
}

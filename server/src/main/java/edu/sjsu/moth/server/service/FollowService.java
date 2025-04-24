package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@CommonsLog
@Configuration
public class FollowService {

    @Autowired
    private FollowRepository followRepository;



    public Mono<Relationship> followUser(String followerId, String followedId) {
        var followResult = saveFollow(followerId, followedId);
        return followResult.flatMap(followStatus -> followRepository.findIfFollows(followedId, followerId)
                .map(follow -> new Relationship(followerId, true, false, false, true, false, false, false, false, false,
                                                false, false, false, "")).switchIfEmpty(Mono.just(
                        new Relationship(followerId, true, false, false, false, false, false, false, false, false,
                                         false, false, false, ""))));
    }

    public Mono<Relationship> unfollowUser(String followerId, String followedId) {
        var followResult = saveFollow(followerId, followedId);
        return followResult.flatMap(followStatus -> followRepository.findIfFollows(followedId, followerId)
                .map(follow -> new Relationship(followerId, false, false, false, false, false, false, false, false, false,
                                                false, false, false, "")).switchIfEmpty(Mono.just(
                        new Relationship(followerId, false, false, false, false, false, false, false, false, false,
                                         false, false, false, ""))));
    }


    public Mono<Follow> saveFollow(String followerId, String followedId) {
        return followRepository.findIfFollows(followerId, followedId).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(followerId, followedId);
            return followRepository.save(follow);
        }));
    }
}

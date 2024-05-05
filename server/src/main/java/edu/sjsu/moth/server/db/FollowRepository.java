package edu.sjsu.moth.server.db;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FollowRepository extends ReactiveMongoRepository<Follow, Follow.FollowKey> {
    @Query("{'id.followedId': ?0}")
    Mono<List<Follow>> findAllByIdFollowedId(String followed_id);
    @Query("{'id.followerId': ?0}")
    Mono<List<Follow>> findAllByIdFollowerId(String follower_id);
//    Mono<Integer> countAllByIdFollowedId(String followed_id);
//    Mono<Integer> countAllByIdFollowerId(String follower_id);
}

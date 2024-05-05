package edu.sjsu.moth.server.db;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FollowRepository extends ReactiveMongoRepository<Follow, Follow.FollowKey> {
    @Query("{'id.followed_id': ?0}")
    Mono<List<Follow>> findAllByFollowedId(String followed_id);
    @Query("{'id.follower_id': ?0}")
    Mono<List<Follow>> findAllByFollowerId(String follower_id);
//    Mono<Integer> countAllByIdFollowedId(String followed_id);
//    Mono<Integer> countAllByIdFollowerId(String follower_id);
}

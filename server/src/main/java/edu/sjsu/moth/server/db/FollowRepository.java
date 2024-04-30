package edu.sjsu.moth.server.db;

import java.util.List;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FollowRepository extends ReactiveMongoRepository<Follow, String> {
    Mono<List<Follow>> findAllByFollowed_id(String followed_id);
    Mono<List<Follow>> findAllByFollower_id(String follower_id);
    Mono<Integer> countAllByFollowed_id(String followed_id);
    Mono<Integer> countAllByFollower_id(String follower_id);
}

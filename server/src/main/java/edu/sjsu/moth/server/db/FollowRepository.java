package edu.sjsu.moth.server.db;

import java.util.List;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FollowRepository extends ReactiveMongoRepository<Follow, Follow.FollowKey> {
    Mono<List<Follow>> findAllByIdFollowedId(String followed_id);
    Mono<List<Follow>> findAllByIdFollowerId(String follower_id);
    Mono<Integer> countAllByIdFollowedId(String followed_id);
    Mono<Integer> countAllByIdFollowerId(String follower_id);
}

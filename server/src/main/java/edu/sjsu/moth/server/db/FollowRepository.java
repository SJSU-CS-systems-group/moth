package edu.sjsu.moth.server.db;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FollowRepository extends ReactiveMongoRepository<Follow, Follow.FollowKey> {
    @Query("{'_id.followed_id': ?0}")
    Flux<Follow> findAllByFollowedId(String followed_id);

    @Query("{'_id.follower_id': ?0}")
    Flux<Follow> findAllByFollowerId(String follower_id);

    @Query("{'_id.follower_id': ?0, '_id.followed_id': ?1}")
    Mono<Follow> findIfFollows(String followerId, String followedId);
}

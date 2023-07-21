package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FollowingRepository extends ReactiveMongoRepository<Following, String>{
    @Query("{id:'?0'}")
    Mono<Following> findItemById(String id);
}

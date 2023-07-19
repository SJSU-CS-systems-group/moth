package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FollowersRepository extends ReactiveMongoRepository<Followers, String>{
    @Query("{id:'?0'}")
    Mono<Followers> findItemById(String id);

}

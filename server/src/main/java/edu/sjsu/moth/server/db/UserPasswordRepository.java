package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface UserPasswordRepository extends ReactiveMongoRepository<UserPassword, String> {
    @Query("{user:'?0'}")
    Mono<UserPassword> findItemByUser(String user);
}
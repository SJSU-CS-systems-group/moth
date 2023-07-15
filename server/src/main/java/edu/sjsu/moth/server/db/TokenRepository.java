package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface TokenRepository extends ReactiveMongoRepository<Token, String> {
    @Query("{token:'?0'}")
    Mono<Token> findItemByToken(String token);

    @Query("{user : ?1}")
    Mono<Token> findItemByUser(String username);
}

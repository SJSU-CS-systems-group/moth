package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface WebfingerRepository extends ReactiveMongoRepository<WebfingerAlias, String> {
    @Query("{alias:'?0'}")
    Mono<WebfingerAlias> findItemByName(String alias);
}

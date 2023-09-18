package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Actor;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ExternalActorRepository extends ReactiveMongoRepository<Actor, String> {
    Mono<Actor> findItemById(String id);
}
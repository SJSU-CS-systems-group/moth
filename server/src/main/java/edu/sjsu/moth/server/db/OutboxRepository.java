package edu.sjsu.moth.server.db;

import edu.sjsu.moth.server.activitypub.message.CreateMessage;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxRepository extends ReactiveMongoRepository<CreateMessage, String> {

    @Query("{ 'actor': ?0 }")
    Flux<CreateMessage> findAllByActorOrderByPublishedAtDesc(String actor);

    @Query(value = "{ 'actor': ?0 }", count = true)
    Mono<Long> countAllByActor(String actor);
}

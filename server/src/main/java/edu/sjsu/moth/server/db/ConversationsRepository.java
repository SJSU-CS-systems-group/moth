package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ConversationsRepository extends ReactiveMongoRepository<Conversations, String>{
    @Query("{id:'?0'}")
    Mono<Conversations> findConversationsById(String id);

}
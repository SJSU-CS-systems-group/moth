package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.CustomEmoji;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CustomEmojiRepository extends ReactiveMongoRepository<CustomEmoji, String> {
    Flux<CustomEmoji> findAll();
}
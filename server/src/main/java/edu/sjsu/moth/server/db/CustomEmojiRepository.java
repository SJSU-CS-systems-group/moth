package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomEmojiRepository extends ReactiveMongoRepository<CustomEmoji, String> {

    @Query("{}")
    Flux<CustomEmoji> findAll();

    @Query("{_id: ?0}")
    Mono<CustomEmoji> findById(String id);

    //not sure if these query methods should be defined :(
    @Query("{shortcode: ?0}")
    Mono<CustomEmoji> findByShortcode(String shortcode);

    @Query("{url: ?0}")
    Mono<CustomEmoji> findByUrl(String url);

    @Query("{staticUrl: ?0}")
    Mono<CustomEmoji> findByStaticUrl(String staticUrl);

    @Query("{visibleInPicker: true}")
    Flux<CustomEmoji> findVisibleCustomEmojis();

    @Query("{category: ?0}")
    Flux<CustomEmoji> findByCategory(String category);
}
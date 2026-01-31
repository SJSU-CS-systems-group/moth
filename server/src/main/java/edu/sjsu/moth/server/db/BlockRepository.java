package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BlockRepository extends ReactiveMongoRepository<Block, Block.BlockKey> {

    @Query("{'_id.blocker_id': ?0}")
    Flux<Block> findAllByBlockerId(String blocker_id);

    @Query("{'_id.blocked_id': ?0}")
    Flux<Block> findAllByBlockedId(String blocked_id);

    @Query("{'_id.blocker_id': ?0, '_id.blocked_id': ?1}")
    Mono<Block> findByBlockerIdAndBlockedId(String blocker_id, String blocked_id);
}

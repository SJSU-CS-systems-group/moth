package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MuteRepository extends ReactiveMongoRepository<Mute, Mute.MuteKey> {

    @Query("{'_id.muter_id': ?0}")
    Flux<Mute> findAllByMuterId(String muter_id);

    @Query("{'_id.muted_id': ?0}")
    Flux<Mute> findAllByMutedId(String muted_id);

    @Query("{'_id.muter_id': ?0, '_id.muted_id': ?1}")
    Mono<Mute> findByMuterIdAndMutedId(String muter_id, String muted_id);
}

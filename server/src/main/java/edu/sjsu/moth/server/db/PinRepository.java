package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PinRepository extends ReactiveMongoRepository<Pin, Pin.PinKey> {

    @Query("{'_id.account_id': ?0}")
    Flux<Pin> findAllByAccountId(String account_id);

    @Query("{'_id.account_id': ?0, '_id.status_id': ?1}")
    Mono<Pin> findByAccountIdAndStatusId(String account_id, String status_id);
}

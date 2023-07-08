package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface PubKeyPairRepository extends ReactiveMongoRepository<PubKeyPair, String> {
    Mono<PubKeyPair> findItemByAcct(String acct);
}
package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface AccountRepository extends ReactiveMongoRepository<Account, String> {
    @Query("{id:'?0'}")
    Mono<Account> findItemByAcct(String acct);
}

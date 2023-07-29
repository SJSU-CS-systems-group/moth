package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AccountRepository extends ReactiveMongoRepository<Account, String> {
    Mono<Account> findItemByAcct(String acct);

    @Query("{ 'acct': { $regex: '^?0', $options: 'i' } }")
        //// regex: ?#, the number referring to the args passed thru the method. it will search based off of args[#],
        // which is acct. if additional args, can pass by doing ?1, ?2, etc.
        //// options: 'i' makes search case-insensitive
    Flux<Account> findByAcctLike(String acct);
}

package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface PubKeyPairRepository extends ReactiveMongoRepository<PubKeyPair, String> {
    // we are putting the DKIM key into the database with accounts, so use a name that cannot be an account
    // spaces normally get trimmed, and emojis can't be used in the account name
    String DKIM_ACCT_ID = " 😀DKIM😀 ";

    Mono<PubKeyPair> findItemByAcct(String acct);
}
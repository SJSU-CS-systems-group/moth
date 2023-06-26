package edu.sjsu.moth.server;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface AccountRepository extends MongoRepository<Account, String> {
    @Query("{id:'?0'}")
    Account findItemByAcct(String acct);
}

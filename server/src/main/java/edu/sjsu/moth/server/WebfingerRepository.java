package edu.sjsu.moth.server;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface WebfingerRepository extends MongoRepository<WebfingerAlias, String> {
    @Query("{alias:'?0'}")
    WebfingerAlias findItemByName(String alias);
}

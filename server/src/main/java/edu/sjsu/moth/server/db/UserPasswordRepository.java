package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserPasswordRepository extends MongoRepository<UserPassword, String> {
    @Query("{user:'?0'}")
    UserPassword findItemByUser(String user);
}
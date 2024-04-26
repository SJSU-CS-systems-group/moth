package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.List;

public interface GroupRepository extends ReactiveMongoRepository<Account, String> {
    @Query("{'mentions.id': { $in: ?0 }}")
    Flux<Account> findByStatusMentionIds(String statusMentionIds);

}
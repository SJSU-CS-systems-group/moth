package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Status;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface StatusRepository extends ReactiveMongoRepository<Status, String> {
    @Query("{id:'?0'}")
    Mono<Status> findStatusById(String id);
}

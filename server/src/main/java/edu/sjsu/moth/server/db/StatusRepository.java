package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Status;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StatusRepository extends ReactiveMongoRepository<Status, String>,
                                          ReactiveQuerydslPredicateExecutor<Status> {

    @Query("{status:'?0'}")
    Mono<Status> findItemByStatus(String status);

    @Query("{ 'text': { $regex: '?0', $options: 'i' } }")
    Flux<Status> findByStatusLike(String status);

}

package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Status;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface StatusRepository
        extends ReactiveMongoRepository<Status, String>, ReactiveQuerydslPredicateExecutor<Status> {

    @Query("{status:'?0'}")
    Mono<Status> findItemByStatus(String status);

    /**
     * the argument is interpolated into a $regex — callers must escape user input
     * with Util.escapeRegex() first.
     */
    @Query("{ 'text': { $regex: '?0', $options: 'i' } }")
    Flux<Status> findByStatusLike(String status);

    @Query("{ 'inReplyToId': ?0 }")
    Flux<Status> findByInReplyToId(String inReplyToId);

    @Query("{ 'tags.name': ?0 }")
    Flux<Status> findByTagName(String tagName);

    @Query("{ 'account.id': { $in: ?0 } }")
    Flux<Status> findByAccountIdIn(List<String> accountIds);

}

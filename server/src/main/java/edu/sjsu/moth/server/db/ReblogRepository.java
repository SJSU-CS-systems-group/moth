package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReblogRepository extends ReactiveMongoRepository<Reblog, Reblog.ReblogKey> {

    @Query("{'_id.account_id': ?0}")
    Flux<Reblog> findAllByAccountId(String account_id);

    @Query("{'_id.status_id': ?0}")
    Flux<Reblog> findAllByStatusId(String status_id);

    @Query("{'_id.account_id': ?0, '_id.status_id': ?1}")
    Mono<Reblog> findByAccountIdAndStatusId(String account_id, String status_id);

    @Query(value = "{'_id.status_id': ?0}", count = true)
    Mono<Long> countByStatusId(String status_id);

    @Query("{'reblog_status_id': ?0}")
    Mono<Reblog> findByReblogStatusId(String reblog_status_id);
}

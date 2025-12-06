package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import reactor.core.publisher.Flux;

public interface ExternalStatusRepository
        extends ReactiveMongoRepository<ExternalStatus, String>, ReactiveQuerydslPredicateExecutor<ExternalStatus> {
    Flux<ExternalStatus> findAllByAccount_AcctOrderByCreatedAtDesc(String acct);
}

package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;

public interface StatusHistoryRepository extends ReactiveMongoRepository<StatusEditCollection, String>,
        ReactiveQuerydslPredicateExecutor<StatusEditCollection> {}

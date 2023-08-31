package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;

public interface ExternalStatusRepository extends ReactiveMongoRepository<ExternalStatus, String>,
                                                  ReactiveQuerydslPredicateExecutor<ExternalStatus> {}
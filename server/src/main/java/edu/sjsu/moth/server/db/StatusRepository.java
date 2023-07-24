package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Status;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;

public interface StatusRepository extends ReactiveMongoRepository<Status, String>,
                                          ReactiveQuerydslPredicateExecutor<Status> {}

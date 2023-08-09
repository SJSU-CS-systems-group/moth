package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface TimelineRepository extends ReactiveMongoRepository<TimelineRecord, String> {}

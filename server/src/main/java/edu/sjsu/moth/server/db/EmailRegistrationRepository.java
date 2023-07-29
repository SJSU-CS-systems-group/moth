package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface EmailRegistrationRepository extends ReactiveMongoRepository<EmailRegistration, String> {}
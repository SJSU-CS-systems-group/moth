package edu.sjsu.moth.server.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface UserListRepository extends ReactiveMongoRepository<UserList, String> {

    @Query("{'owner_id': ?0}")
    Flux<UserList> findAllByOwnerId(String owner_id);

    @Query("{'owner_id': ?0, 'account_ids': ?1}")
    Flux<UserList> findByOwnerIdContainingAccount(String owner_id, String account_id);
}

package edu.sjsu.moth.server.db;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FollowersRepository extends ReactiveMongoRepository<Followers, String>{
    @Query("{id:'?0'}")
    Mono<Followers> findItemById(String id);

    @Query("{id:'?0'}")
    Flux<Followers> findItemByIdAndPage(String id, Pageable pageable);

}

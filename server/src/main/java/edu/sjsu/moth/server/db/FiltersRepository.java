package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Filter;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface FiltersRepository extends ReactiveMongoRepository<Filter, String> {
    Flux<Filter> findAll();

}

package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class ActorService {

    @Autowired
    ExternalActorRepository externalActorRepository;

    public Mono<Actor> save(Actor actor) {
        return externalActorRepository.save(actor);
    }

    public Mono<Actor> getActor(String actor) {return externalActorRepository.findItemById(actor);}
}
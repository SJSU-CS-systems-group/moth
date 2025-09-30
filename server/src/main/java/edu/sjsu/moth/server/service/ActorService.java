package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.activitypub.service.WebfingerService;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ActorService {

    private final ExternalActorRepository externalActorRepository;
    private final WebfingerService webfingerService;
    private final WebClient webClient;

    public ActorService(ExternalActorRepository externalActorRepository, WebfingerService webfingerService,
                        WebClient.Builder webClientBuilder) {
        this.externalActorRepository = externalActorRepository;
        this.webfingerService = webfingerService;
        this.webClient = webClientBuilder.build();
    }

    public Mono<Actor> save(Actor actor) {
        return externalActorRepository != null ? externalActorRepository.save(actor) : Mono.just(actor);
    }

    public Mono<Actor> getActor(String actor) {
        return externalActorRepository != null ? externalActorRepository.findItemById(actor) : Mono.empty();
    }

    public Mono<Actor> fetchAndSaveActorById(String actorId) {
        return webClient.get().uri(actorId).accept(MediaType.parseMediaType("application/activity+json")).retrieve()
                .bodyToMono(Actor.class).flatMap(this::save);
    }

    public Mono<Account> resolveRemoteAccount(String userHandle) {
        return webfingerService.discoverProfileUrl(userHandle).flatMap(profileUrl -> webClient.get().uri(profileUrl)
                        .accept(MediaType.parseMediaType("application/activity+json")).retrieve().bodyToMono(Actor.class))
                .flatMap(actor -> save(actor).then(InboxService.convertToAccount(actor)));
    }
}

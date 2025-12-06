package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.activitypub.ActivityPubUtil;
import edu.sjsu.moth.server.activitypub.service.WebfingerService;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Service
@CommonsLog
public class ActorService {

    private final ExternalActorRepository externalActorRepository;
    private final WebfingerService webfingerService;
    private final WebClient webClient;

    @Autowired
    @Lazy
    private AccountService accountService;

    public ActorService(ExternalActorRepository externalActorRepository,
                        WebfingerService webfingerService,
                        WebClient.Builder webClientBuilder) {
        this.externalActorRepository = externalActorRepository;
        this.webfingerService = webfingerService;
        this.webClient = webClientBuilder.build();
    }

    public Mono<Actor> save(Actor actor) {
        return externalActorRepository.save(actor);
    }

    public Mono<Actor> getActor(String actor) {
        return externalActorRepository.findItemById(actor);
    }

    public Mono<Account> resolveRemoteAccount(String userHandle) {
        return webfingerService.discoverProfileUrl(userHandle)
                .flatMap(profileUrl -> webClient.get().uri(profileUrl)
                        .accept(MediaType.parseMediaType("application/activity+json"))
                        .retrieve()
                        .bodyToMono(Actor.class))
                .flatMap(actor -> save(actor)
                        .then(accountService.convertToAccount(actor)));
    }

    public Mono<Actor> fetchActor(String actorUri) {
        URI uri = URI.create(actorUri);
        String actorId = ActivityPubUtil.inboxUrlToAcct(actorUri);
        return getActor(actorId)
                .switchIfEmpty(Mono.defer(() -> fetchRemoteActor(uri)));
    }

    private Mono<Actor> fetchRemoteActor(URI actorUri) {
        WebClient client = WebClient.builder().defaultHeaders(httpHeaders -> {
            httpHeaders.setAccept(List.of(
                    MediaType.valueOf("application/activity+json"),
                    MediaType.valueOf("application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\""),
                    MediaType.APPLICATION_JSON
            ));
        }).build();

        return client.get().uri(actorUri).retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> res.bodyToMono(String.class).flatMap(body -> {
                    log.error("4xx error fetching actor " + actorUri + ": " + body);
                    return Mono.error(new RuntimeException("Client error: " + body));
                }))
                .onStatus(HttpStatusCode::is5xxServerError, res -> res.bodyToMono(String.class).flatMap(body -> {
                    log.error("5xx error fetching actor " + actorUri + ": " + body);
                    return Mono.error(new RuntimeException("Server error: " + body));
                }))
                .bodyToMono(Actor.class)
                .flatMap(this::save)
                .doOnSuccess(actor -> log.info("Successfully fetched & saved actor " +
                                                       (actor != null ? actor.id : "(null)") + " from " + actorUri));
    }
}

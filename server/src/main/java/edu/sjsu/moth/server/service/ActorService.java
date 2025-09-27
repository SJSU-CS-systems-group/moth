package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.activitypub.service.WebfingerService;
import edu.sjsu.moth.server.controller.InboxController;
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
                .flatMap(actor -> save(actor).then(InboxController.convertToAccount(actor)));
    }

    public Mono<Account> resolveRemoteAccountFromProfileUrl(String profileUrl) {
        return webClient.get().uri(profileUrl)
                .accept(MediaType.parseMediaType("application/activity+json"))
                .retrieve()
                .bodyToMono(Actor.class)
                .flatMap(actor -> save(actor).then(InboxController.convertToAccount(actor)))
                .onErrorResume(e -> {
                    try {
                        java.net.URI uri = java.net.URI.create(profileUrl);
                        String host = uri.getHost();
                        String path = uri.getPath();
                        if (host == null || path == null) {
                            return Mono.error(e);
                        }
                        String username = null;
                        if (path.startsWith("/users/")) {
                            String[] segments = path.split("/");
                            if (segments.length >= 3) {
                                username = segments[2];
                            }
                        } else if (path.startsWith("/@")) {
                            String[] segments = path.split("/");
                            if (segments.length >= 2) {
                                username = segments[1].startsWith("@") ? segments[1].substring(1) : segments[1];
                            }
                        }
                        if (username != null) {
                            String handle = username + "@" + host;
                            return resolveRemoteAccount(handle);
                        }
                    } catch (Exception ignored) {
                    }
                    return Mono.error(e);
                });
    }
}

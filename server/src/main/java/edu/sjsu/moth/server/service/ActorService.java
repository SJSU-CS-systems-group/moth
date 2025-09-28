package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.activitypub.service.WebfingerService;
import edu.sjsu.moth.server.controller.InboxController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@CommonsLog
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

    private Mono<Account> fetchAndConvertActor(String url) {
        // try to get from the local DB, URL = https://mastodon.social/users/divyamonmastodon
        return getActor(url).flatMap(InboxController::convertToAccount).switchIfEmpty(Mono.defer(() -> {

            // If not found, fetch from remote server
            log.debug("Actor " + url + " not in DB, fetching from remote");
            return webClient.get().uri(url).accept(MediaType.parseMediaType("application/activity+json")).retrieve()
                    .bodyToMono(Actor.class)
                    .flatMap(actor -> save(actor).then(InboxController.convertToAccount(actor)));
        }));
    }

    private Mono<Account> resolveRemoteAccountByHandle(String userHandle) {
        return webfingerService.discoverProfileUrl(userHandle).flatMap(this::fetchAndConvertActor);
    }

    private String extractUsernameFromPath(String path) {
        if (path == null || !path.startsWith("/@")) {
            return null; // Path must start with /@
        }
        String potentialUsername = path.substring(2);
        // The username must not be empty and must not contain any further path segments.
        if (potentialUsername.isEmpty() || potentialUsername.contains("/")) {
            return null;
        }
        return potentialUsername;
    }

    private String getCanonicalActorId(String profileUrl) {
        try {
            java.net.URI uri = java.net.URI.create(profileUrl);
            String host = uri.getHost();
            String path = uri.getPath(); // path = /@divyamonmastodon in https://mastodon.social/@amazing_justDivyam
            if (host == null || path == null) {
                return profileUrl; // Not parsable URL
            }
            String username = extractUsernameFromPath(path);
            if (username != null) {
                return "https://" + host + "/users/" + username;
            }
        } catch (Exception ignored) {
            // if URI parsing fails, just fall through
        }
        return profileUrl; // Return original if no translation is needed or possible
    }

    private Mono<Account> resolveRemoteAccountFromProfileUrl(String profileUrl) {
        String canonicalId = getCanonicalActorId(profileUrl);
        return fetchAndConvertActor(canonicalId).onErrorResume(e -> {
            log.info("Failed to fetch actor from URL " + canonicalId + ". Attempting fallback to handle resolution.");
            try {
                java.net.URI uri = java.net.URI.create(canonicalId);
                String host = uri.getHost();
                String path = uri.getPath();
                if (host == null || path == null) {
                    return Mono.error(e);
                }
                // Use the already extracted username from the canonical ID
                if (path.startsWith("/users/")) {
                    String username = path.substring("/users/".length());
                    String handle = username + "@" + host;
                    log.info("Fallback successful, resolving handle: " + handle);
                    return resolveRemoteAccountByHandle(handle);
                }
            } catch (Exception ignored) {
                // if URI parsing fails, just fall through to the original error
            }
            return Mono.error(e);
        });
    }

    public Mono<Account> resolveRemoteAccount(String handleOrUrl) {
        System.out.println("resolveRemoteAccount");
        if (handleOrUrl.startsWith("http://") || handleOrUrl.startsWith("https://")) {
            return resolveRemoteAccountFromProfileUrl(handleOrUrl);
        } else if (handleOrUrl.contains("@")) {
            String processedHandle = handleOrUrl.startsWith("@") ? handleOrUrl.substring(1) : handleOrUrl;
            return resolveRemoteAccountByHandle(processedHandle);
        }
        // Not a format we can resolve remotely, return empty.
        return Mono.empty();
    }
}

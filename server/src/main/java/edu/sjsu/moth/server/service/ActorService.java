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
    AccountService accountService;

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

    public Mono<Actor> fetchActor(String actorUri) {
        URI uri = URI.create(actorUri);
        String actorId = ActivityPubUtil.inboxUrlToAcct(actorUri);
        return getActor(actorId).switchIfEmpty(Mono.defer(() -> fetchRemoteActor(uri)));
    }

    public Mono<Actor> fetchAndSaveActorById(String actorId) {
        return webClient.get().uri(actorId).accept(MediaType.parseMediaType("application/activity+json")).retrieve()
                .bodyToMono(Actor.class).flatMap(this::save);
    }

    private Mono<Actor> fetchRemoteActor(URI actorUri) {
        WebClient client = WebClient.builder().defaultHeaders(httpHeaders -> {
            httpHeaders.setAccept(List.of(MediaType.valueOf("application/activity+json"), MediaType.valueOf(
                                                  "application/ld+json; profile=\"https://www.w3" + ".org/ns" +
                                                          "/activitystreams\""),
                                          MediaType.APPLICATION_JSON));
        }).build();

        return client.get().uri(actorUri).retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> res.bodyToMono(String.class).flatMap(body -> {
                    log.error("4xx error fetching actor " + actorUri + ": " + body);
                    return Mono.error(new RuntimeException("Client error: " + body));
                })).onStatus(HttpStatusCode::is5xxServerError, res -> res.bodyToMono(String.class).flatMap(body -> {
                    log.error("5xx error fetching actor " + actorUri + ": " + body);
                    return Mono.error(new RuntimeException("Server error: " + body));
                })).bodyToMono(Actor.class).flatMap(this::save).doOnSuccess(actor -> log.info(
                        "Successfully fetched & saved actor " + (actor != null ? actor.id : "(null)") + " from " +
                                actorUri));
    }

    private Mono<Account> resolveRemoteAccountByHandle(String userHandle) {
        return webfingerService.discoverProfileUrl(userHandle).flatMap(this::fetchAndConvertActor);
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

    private Mono<Account> fetchAndConvertActor(String url) {
        // try to get from the local DB, URL = https://mastodon.social/users/divyamonmastodon
        return getActor(url).flatMap(accountService::convertToAccount).switchIfEmpty(Mono.defer(() -> {

            // If not found, fetch from remote server
            log.debug("Actor " + url + " not in DB, fetching from remote");
            return webClient.get().uri(url).accept(MediaType.parseMediaType("application/activity+json")).retrieve()
                    .bodyToMono(Actor.class).flatMap(actor -> save(actor).then(accountService.convertToAccount(actor)));
        }));
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
}

package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.controller.InboxController;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
@CommonsLog
public class BackfillService {

    private final ActorService actorService;
    private final RemoteOutboxFetcher remoteOutboxFetcher;
    private final RemoteStatusIngestService remoteStatusIngestService;
    private final int followMaxStatuses;
    private final int followMaxDays;
    private final int searchMaxStatuses;
    private final int searchMaxDays;
    private final int maxConcurrency;
    private final int cooldownMinutes;
    private final Semaphore semaphore;
    private final Map<String, Instant> lastRunByAcct = new ConcurrentHashMap<>();

    public BackfillService(ActorService actorService, RemoteOutboxFetcher remoteOutboxFetcher,
                           RemoteStatusIngestService remoteStatusIngestService) {
        this.actorService = actorService;
        this.remoteOutboxFetcher = remoteOutboxFetcher;
        this.remoteStatusIngestService = remoteStatusIngestService;

        this.followMaxStatuses = 500;
        this.followMaxDays = 30;
        this.searchMaxStatuses = 150;
        this.searchMaxDays = this.followMaxDays;
        this.maxConcurrency = 2;
        this.cooldownMinutes = 60;
        this.semaphore = new Semaphore(Math.max(1, this.maxConcurrency));
    }

    public void backfillRemoteAcctAsync(String acctOrUrl, BackfillType type) {
        if (acctOrUrl == null || acctOrUrl.isBlank()) return;

        // Per-actor cooldown
        Instant last = lastRunByAcct.get(acctOrUrl);
        if (last != null && last.isAfter(Instant.now().minus(Duration.ofMinutes(cooldownMinutes)))) {
            return;
        }
        lastRunByAcct.put(acctOrUrl, Instant.now());

        Mono.defer(() -> doBackfill(acctOrUrl, type)).subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("Backfill failed for " + acctOrUrl + ": " + e.getMessage()))
                .onErrorResume(e -> Mono.empty()).subscribe();
    }

    private Mono<Integer> doBackfill(String acctOrUrl, BackfillType type) {
        // find limits for this backfill based on its type
        int maxStatuses = type == BackfillType.FOLLOW ? followMaxStatuses : searchMaxStatuses;
        Duration maxAge = Duration.ofDays(type == BackfillType.FOLLOW ? followMaxDays : searchMaxDays);

        // backfill operation with count + time based filters
        return acquire().then(resolveActor(acctOrUrl)).flatMap(actor -> {
            Flux<JsonNode> activities = remoteOutboxFetcher.fetchCreateActivities(actor.outbox, null);

            if (maxAge != null && !maxAge.isZero() && !maxAge.isNegative()) {
                // only take activities newer than the cutoff date (time based filter)
                Instant cutoff = Instant.now().minus(maxAge);

                // fetch 'Create' activities from the actor's remote outbox
                activities = activities.takeWhile(item -> {
                    String published = text(item.path("published"));
                    try {
                        // stop when item is older than the cutoff
                        return published != null && !published.isBlank() && Instant.parse(published).isAfter(cutoff);
                    } catch (Exception e) {
                        return false;
                    }
                });
            }

            // only take up to the maximum number of statuses (count based filter)
            if (maxStatuses > 0) {
                activities = activities.take(maxStatuses);
            }

            // pass activities to ingest service to save them to db
            return remoteStatusIngestService.ingestCreateNotes(activities, actor, InboxController::convertToAccount)
                    .map(list -> list != null ? list.size() : 0);

        }).doFinally(s -> release()); // release semaphore permit
    }

    // helpers
    public Mono<Integer> runBackfillOnce(String acctOrUrl, BackfillType type) {
        return doBackfill(acctOrUrl, type);
    }

    private Mono<Void> acquire() {
        return Mono.fromCallable(() -> {
            try {
                semaphore.acquire();
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void release() {
        semaphore.release();
    }

    // Returns full actor
    private Mono<Actor> resolveActor(String acctOrUrl) {
        if (acctOrUrl.startsWith("http://") || acctOrUrl.startsWith("https://")) {
            String actorId = acctOrUrl;
            // get the actor from local storage, if empty, fetch it from the remote server
            return actorService.getActor(actorId).switchIfEmpty(actorService.fetchAndSaveActorById(actorId));
        }
        int at = acctOrUrl.indexOf('@');
        if (at > 0 && at < acctOrUrl.length() - 1) {
            String username = acctOrUrl.substring(0, at);
            String host = acctOrUrl.substring(at + 1);
            String actorId = "https://" + host + "/users/" + username;
            return actorService.getActor(actorId).switchIfEmpty(actorService.fetchAndSaveActorById(actorId));
        }
        return Mono.error(new IllegalArgumentException("Invalid remote identifier: " + acctOrUrl));
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    public enum BackfillType {FOLLOW, SEARCH}
}

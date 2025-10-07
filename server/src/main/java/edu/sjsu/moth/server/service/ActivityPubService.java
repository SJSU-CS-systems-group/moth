package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.server.db.FederatedActivity;
import edu.sjsu.moth.server.db.FederatedActivityRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;

// orchestrating the sending of signed ActivityPub activities.
@Configuration
@CommonsLog
public class ActivityPubService implements AutoCloseable {

    HttpSignatureService httpSignatureService;

    FederatedActivityRepository federatedActivityRepository;

    ScheduledThreadPoolExecutor threadPool;

    ActivityPubService(HttpSignatureService httpSignatureService, FederatedActivityRepository federatedActivityRepository) {
        this.httpSignatureService = httpSignatureService;
        this.federatedActivityRepository = federatedActivityRepository;
        this.threadPool = new ScheduledThreadPoolExecutor(5);
        this.threadPool.scheduleWithFixedDelay(this::scheduled_send, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
    }

    private Mono<Void> asyncSendActivityPubMessage(JsonNode content, String senderActorId, String targetInbox) {
        String contentStr = serializeContent(content);
        FederatedActivity activity = new FederatedActivity(targetInbox, senderActorId, contentStr, Instant.now());;
        return federatedActivityRepository.save(activity).then();
    }

    private boolean isValidFederatedActivity(FederatedActivity activity) {
        return (activity.id != null && activity.content != null && activity.senderActorId != null
                && activity.inboxUrl != null);
    }

    private String serializeContent(JsonNode content) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize content: " + e.getMessage(), e);
            return null;
        }
    }

    private JsonNode deserializeContent(String content)  {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize content: " + e.getMessage(), e);
            return null;
        }
    }

    private void scheduled_send() {
        //TODO: fetched records with attempt < 3
        federatedActivityRepository.findAll().filter(this::isValidFederatedActivity).parallel().runOn(Schedulers.boundedElastic()).doOnNext(activity -> {
            JsonNode content = deserializeContent(activity.content);
            sendSignedActivity(activity.id, content, activity.senderActorId, activity.inboxUrl).publishOn(
                            Schedulers.boundedElastic())
                    .doOnSuccess(v -> {
                        log.info("delete the activity " + activity.id + " after successful send");
                        federatedActivityRepository.deleteById(activity.id).subscribe();
                    })
                    .doOnError(e -> {
                        log.error("Error sending activity to " + activity.inboxUrl + ": " + e.getMessage());
                        activity.attempts = activity.attempts + 1;
                        federatedActivityRepository.save(activity).subscribe();
                    }).subscribe();
        }).subscribe();
    }

    public Mono<Void> sendSignedActivity(JsonNode message, String sendingActorId, String targetInbox) {
       return asyncSendActivityPubMessage(message, sendingActorId, targetInbox);
    }

    private Mono<Void> sendSignedActivity(String DocId, JsonNode message, String sendingActorId, String targetInbox) {
        URI targetUri;
        try {
            targetUri = URI.create(targetInbox);
            if (targetUri.getHost() == null) {
                throw new IllegalArgumentException("Target inbox URI missing host");
            }
        } catch (Exception e) {
            log.error("Invalid target inbox URI: " + targetInbox, e);
            return Mono.error(new IllegalArgumentException("Invalid target inbox URI: " + targetInbox, e));
        }
        final URI finalTargetUri = targetUri;

        byte[] bodyBytes;
        try {
            bodyBytes = new ObjectMapper().writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return httpSignatureService.prepareSignedHeaders(HttpMethod.POST, sendingActorId, finalTargetUri, bodyBytes)
                .flatMap(headers -> {
                    WebClient client =
                            WebClient.builder().defaultHeaders(httpHeaders -> httpHeaders.addAll(headers)).build();
                    log.info("Sending signed activity from " + sendingActorId + " to " + targetInbox);

                    return client.post().uri(targetUri).contentType(MediaType.APPLICATION_JSON).bodyValue(message)
                            .retrieve().onStatus(HttpStatusCode::is4xxClientError,
                                                 res -> res.bodyToMono(String.class).flatMap(body -> {
                                                     log.error("4xx error sending activity to " + targetInbox + ": " +
                                                                       body);
                                                     return Mono.error(new RuntimeException("Client error: " + body));
                                                 })).onStatus(HttpStatusCode::is5xxServerError,
                                                              res -> res.bodyToMono(String.class).flatMap(body -> {
                                                                  log.error("5xx error sending activity to " +
                                                                                    targetInbox + ": " + body);
                                                                  return Mono.error(new RuntimeException(
                                                                          "Server error: " + body));
                                                              })).bodyToMono(String.class)
                            .doOnSuccess(response -> log.info("Successfully sent activity to " + targetInbox)).then();
                }).onErrorResume(e -> {
                    log.error("Failed pipeline before/during sending signed activity to " + targetInbox + ": " +
                                      e.getMessage(), e);
                    return Mono.error(e);
                });
    }

    @Override
    public void close() {
        this.threadPool.shutdownNow();
    }
}

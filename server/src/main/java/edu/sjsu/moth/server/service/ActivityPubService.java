package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.server.db.FederatedActivity;
import edu.sjsu.moth.server.db.FederatedActivityRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

// orchestrating the sending of signed ActivityPub activities.
@CommonsLog
@Configuration
public class ActivityPubService {

    @Autowired
    HttpSignatureService httpSignatureService;

    @Autowired
    FederatedActivityRepository federatedActivityRepository;

    private Mono<Void> asyncSendActivityPubMessage(JsonNode content, String senderActorId, String targetInbox) {
        FederatedActivity activity = new FederatedActivity(targetInbox, senderActorId, content, Instant.now());;
        return federatedActivityRepository.save(activity).then();
    }

    public Mono<Void> sendSignedActivity(JsonNode message, String sendingActorId, String targetInbox) {
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

                    return asyncSendActivityPubMessage(message, sendingActorId, targetInbox).then(client.post().uri(targetUri).contentType(MediaType.APPLICATION_JSON).bodyValue(message)
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
                            .doOnSuccess(response -> log.info("Successfully sent activity to " + targetInbox)).then());
                }).onErrorResume(e -> {
                    log.error("Failed pipeline before/during sending signed activity to " + targetInbox + ": " +
                                      e.getMessage(), e);
                    return Mono.error(e);
                });
    }
}
